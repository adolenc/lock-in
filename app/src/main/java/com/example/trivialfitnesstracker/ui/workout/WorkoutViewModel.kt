package com.example.trivialfitnesstracker.ui.workout

import androidx.lifecycle.*
import com.example.trivialfitnesstracker.data.WorkoutRepository
import com.example.trivialfitnesstracker.data.entity.DayOfWeek
import com.example.trivialfitnesstracker.data.entity.Exercise
import com.example.trivialfitnesstracker.data.entity.SetLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ExerciseHistory(
    val date: String,
    val sets: List<SetLog>
)

data class ExerciseStatus(
    val index: Int,
    val hasSets: Boolean
)

class WorkoutViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    private var sessionId: Long = 0
    private var currentDay: DayOfWeek? = null
    private var exercises: List<Exercise> = emptyList()
    private var currentIndex = 0
    private var lastLoggedSetId: Long? = null

    private val _currentExercise = MutableLiveData<Exercise?>()
    val currentExercise: LiveData<Exercise?> = _currentExercise

    private val _progress = MutableLiveData<Pair<Int, Int>>() // current, total
    val progress: LiveData<Pair<Int, Int>> = _progress

    private val _exerciseStatuses = MutableLiveData<List<ExerciseStatus>>()
    val exerciseStatuses: LiveData<List<ExerciseStatus>> = _exerciseStatuses

    private val _history = MutableLiveData<List<ExerciseHistory>>()
    val history: LiveData<List<ExerciseHistory>> = _history

    private val _todaySets = MutableLiveData<List<SetLog>>()
    val todaySets: LiveData<List<SetLog>> = _todaySets

    private val _reps = MutableLiveData(10)
    val reps: LiveData<Int> = _reps

    private val _lastReps = MutableLiveData<Int?>()
    val lastReps: LiveData<Int?> = _lastReps

    private val _lastWeight = MutableLiveData<Float?>()
    val lastWeight: LiveData<Float?> = _lastWeight

    private val _isFinished = MutableLiveData(false)
    val isFinished: LiveData<Boolean> = _isFinished

    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> = _canUndo

    fun startWorkout(day: DayOfWeek) {
        viewModelScope.launch {
            exercises = repository.getExercisesForDaySync(day)
            if (exercises.isEmpty()) {
                _isFinished.value = true
                return@launch
            }
            sessionId = repository.startWorkoutSession(day)
            currentDay = day
            currentIndex = 0
            loadCurrentExercise()
            updateExerciseStatuses()
        }
    }

    fun resumeWorkout(day: DayOfWeek, savedSessionId: Long, savedIndex: Int) {
        viewModelScope.launch {
            exercises = repository.getExercisesForDaySync(day)
            if (exercises.isEmpty()) {
                _isFinished.value = true
                return@launch
            }
            sessionId = savedSessionId
            currentDay = day
            currentIndex = savedIndex.coerceIn(0, exercises.size - 1)
            loadCurrentExercise()
            updateExerciseStatuses()
        }
    }

    fun getSessionInfo(): Pair<Long, DayOfWeek>? {
        val day = currentDay ?: return null
        return Pair(sessionId, day)
    }

    private suspend fun loadCurrentExercise() {
        if (currentIndex >= exercises.size) {
            _isFinished.value = true
            return
        }

        val exercise = exercises[currentIndex]
        _currentExercise.value = exercise
        _progress.value = Pair(currentIndex + 1, exercises.size)

        // Load history
        val recentLogs = repository.getRecentLogsForExercise(exercise.id, 3)
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val historyList = recentLogs.map { log ->
            val sets = repository.getSetsForExerciseLog(log.id)
            ExerciseHistory(
                date = dateFormat.format(Date(log.completedAt)),
                sets = sets
            )
        }
        _history.value = historyList

        // Get last weight and reps used
        val lastSets = historyList.firstOrNull()?.sets?.filter { !it.isDropdown }
        _lastWeight.value = lastSets?.firstOrNull()?.weight
        _lastReps.value = lastSets?.firstOrNull()?.reps

        // Load today's sets
        loadTodaySets(exercise.id)
        
        // Reset undo state when changing exercises
        lastLoggedSetId = null
        _canUndo.value = false
    }

    private suspend fun loadTodaySets(exerciseId: Long) {
        val exerciseLog = repository.getOrCreateExerciseLog(sessionId, exerciseId)
        val sets = repository.getSetsForExerciseLog(exerciseLog.id)
        _todaySets.value = sets
        updateExerciseStatuses()
    }

    private suspend fun updateExerciseStatuses() {
        val statuses = exercises.mapIndexed { index, exercise ->
            val log = repository.getExerciseLogForSession(sessionId, exercise.id)
            val hasSets = if (log != null) {
                repository.getSetsForExerciseLog(log.id).isNotEmpty()
            } else false
            ExerciseStatus(index, hasSets)
        }
        _exerciseStatuses.value = statuses
    }

    fun setReps(value: Int) {
        _reps.value = value
    }

    fun incrementReps() {
        _reps.value = (_reps.value ?: 10) + 1
    }

    fun decrementReps() {
        val current = _reps.value ?: 10
        if (current > 1) _reps.value = current - 1
    }

    fun logSet(weight: Float) {
        viewModelScope.launch {
            val exercise = _currentExercise.value ?: return@launch
            val exerciseLog = repository.getOrCreateExerciseLog(sessionId, exercise.id)
            lastLoggedSetId = repository.logSet(exerciseLog.id, weight, _reps.value ?: 10, isDropdown = false)
            _canUndo.value = true
            loadTodaySets(exercise.id)
        }
    }

    fun logDropdown() {
        viewModelScope.launch {
            val exercise = _currentExercise.value ?: return@launch
            val exerciseLog = repository.getOrCreateExerciseLog(sessionId, exercise.id)
            lastLoggedSetId = repository.logSet(exerciseLog.id, null, _reps.value ?: 10, isDropdown = true)
            _canUndo.value = true
            loadTodaySets(exercise.id)
        }
    }

    fun undoLastSet() {
        viewModelScope.launch {
            val setId = lastLoggedSetId ?: return@launch
            repository.deleteSetLog(setId)
            lastLoggedSetId = null
            _canUndo.value = false
            val exercise = _currentExercise.value ?: return@launch
            loadTodaySets(exercise.id)
        }
    }

    fun nextExercise() {
        viewModelScope.launch {
            if (currentIndex < exercises.size - 1) {
                currentIndex++
                loadCurrentExercise()
            } else {
                _isFinished.value = true
            }
        }
    }

    fun previousExercise() {
        viewModelScope.launch {
            if (currentIndex > 0) {
                currentIndex--
                loadCurrentExercise()
            }
        }
    }

    fun goToExercise(index: Int) {
        viewModelScope.launch {
            if (index in exercises.indices) {
                currentIndex = index
                loadCurrentExercise()
            }
        }
    }

    fun isFirstExercise() = currentIndex == 0
    fun isLastExercise() = currentIndex >= exercises.size - 1
    fun getCurrentIndex() = currentIndex
}

class WorkoutViewModelFactory(
    private val repository: WorkoutRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return WorkoutViewModel(repository) as T
    }
}
