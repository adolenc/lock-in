package com.example.trivialfitnesstracker.ui.workout

import androidx.lifecycle.*
import com.example.trivialfitnesstracker.data.WorkoutRepository
import com.example.trivialfitnesstracker.data.entity.DayOfWeek
import com.example.trivialfitnesstracker.data.entity.Exercise
import kotlinx.coroutines.launch

data class ExerciseStatus(
    val index: Int,
    val hasSets: Boolean
)

class WorkoutViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    private var sessionId: Long = 0
    private var currentDay: DayOfWeek? = null
    private var dayExercises: List<Exercise> = emptyList()
    private var extraExercises: List<Exercise> = emptyList()
    private val exercises: List<Exercise> get() = dayExercises + extraExercises

    private val _exerciseStatuses = MutableLiveData<List<ExerciseStatus>>()
    val exerciseStatuses: LiveData<List<ExerciseStatus>> = _exerciseStatuses

    private val _isFinished = MutableLiveData(false)
    val isFinished: LiveData<Boolean> = _isFinished

    private val _exercisesList = MutableLiveData<List<Exercise>>()
    val exercisesList: LiveData<List<Exercise>> = _exercisesList

    private val _currentIndex = MutableLiveData(0)
    val currentIndex: LiveData<Int> = _currentIndex

    fun setCurrentIndex(index: Int) {
        if (index != currentIndex.value) {
            _currentIndex.value = index
        }
    }

    fun startWorkout(day: DayOfWeek) {
        viewModelScope.launch {
            dayExercises = repository.getExercisesForDaySync(day)
            if (exercises.isEmpty()) {
                _exercisesList.value = exercises
                _isFinished.value = true
                return@launch
            }
            sessionId = repository.startWorkoutSession(day)
            currentDay = day
            _exercisesList.value = exercises
            _currentIndex.value = 0
            updateExerciseStatuses()
        }
    }

    fun resumeWorkout(day: DayOfWeek, savedSessionId: Long, savedIndex: Int, extraExerciseIds: List<Long> = emptyList()) {
        viewModelScope.launch {
            dayExercises = repository.getExercisesForDaySync(day)
            if (extraExerciseIds.isNotEmpty()) {
                extraExercises = repository.getExercisesByIds(extraExerciseIds)
            }
            if (exercises.isEmpty()) {
                _exercisesList.value = exercises
                _isFinished.value = true
                return@launch
            }
            sessionId = savedSessionId
            currentDay = day
            _exercisesList.value = exercises
            _currentIndex.value = savedIndex.coerceIn(0, exercises.size - 1)
            updateExerciseStatuses()
        }
    }

    fun updateExerciseStatuses() {
        viewModelScope.launch {
            val statuses = exercises.mapIndexed { index, exercise ->
                val log = repository.getExerciseLogForSession(sessionId, exercise.id)
                val hasSets = if (log != null) {
                    repository.getSetsForExerciseLog(log.id).isNotEmpty()
                } else false
                ExerciseStatus(index, hasSets)
            }
            _exerciseStatuses.value = statuses
        }
    }
    
    fun getSessionInfo(): Pair<Long, DayOfWeek>? {
        val day = currentDay ?: return null
        return Pair(sessionId, day)
    }

    fun isFirstExercise() = currentIndex.value == 0
    fun isLastExercise() = currentIndex.value == (exercises.size - 1)
    fun getCurrentIndex() = currentIndex.value ?: 0

    fun goToExercise(index: Int) {
        // Obsolete
    }

    fun addExercises(newExercises: List<Exercise>) {
        val existingIds = exercises.map { it.id }.toSet()
        val toAdd = newExercises.filter { it.id !in existingIds }
        if (toAdd.isEmpty()) return
        val insertPosition = exercises.size
        extraExercises = extraExercises + toAdd
        _exercisesList.value = exercises
        _currentIndex.value = insertPosition
        updateExerciseStatuses()
    }

    suspend fun getExercisesFromOtherDays(): List<Exercise> {
        val day = currentDay ?: return emptyList()
        return repository.getExercisesExcludingDay(day)
    }

    fun getExtraExerciseIds(): List<Long> = extraExercises.map { it.id }

    suspend fun createAndAddExercise(name: String) {
        val day = currentDay ?: return
        val exerciseId = repository.addExercise(name, day)
        val exercise = repository.getExerciseById(exerciseId) ?: return
        addExercises(listOf(exercise))
    }
}

class WorkoutViewModelFactory(
    private val repository: WorkoutRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return WorkoutViewModel(repository) as T
    }
}