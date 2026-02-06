package com.example.trivialfitnesstracker.ui.setup

import androidx.lifecycle.*
import com.example.trivialfitnesstracker.data.WorkoutRepository
import com.example.trivialfitnesstracker.data.entity.DayOfWeek
import com.example.trivialfitnesstracker.data.entity.Exercise
import kotlinx.coroutines.launch

class ExerciseListViewModel(
    private val repository: WorkoutRepository,
    private val day: DayOfWeek
) : ViewModel() {

    val exercises: LiveData<List<Exercise>> = repository.getExercisesForDay(day)
    
    // Map of Exercise ID to formatted last log string
    private val _lastLogs = MutableLiveData<Map<Long, String>>()
    val lastLogs: LiveData<Map<Long, String>> = _lastLogs

    // Trigger update when exercises change
    private val _loadTrigger = exercises.observeForever { exerciseList ->
        loadLastLogs(exerciseList)
    }

    private fun loadLastLogs(exerciseList: List<Exercise>) {
        viewModelScope.launch {
            val logsMap = mutableMapOf<Long, String>()
            exerciseList.forEach { exercise ->
                val logs = repository.getRecentLogsForExercise(exercise.id, 10)
                for (log in logs) {
                    val sets = repository.getSetsForExerciseLog(log.id)
                    if (sets.isEmpty()) continue
                    val regularSets = sets.filter { !it.isDropdown }
                    val dropdownSets = sets.filter { it.isDropdown }
                    
                    val weight = regularSets.firstOrNull()?.weight?.let { "${it.toInt()}kg" } ?: ""
                    val reps = regularSets.joinToString(", ") { it.reps.toString() }
                    val dropdown = if (dropdownSets.isNotEmpty())
                        " + ${dropdownSets.joinToString(", ") { it.reps.toString() }}" else ""
                    
                    val variation = if (log.variationId != null) {
                        repository.getVariationById(log.variationId)?.name
                    } else null
                    val variationPrefix = if (variation != null) "$variation " else ""

                    logsMap[exercise.id] = if (weight.isNotEmpty()) "$variationPrefix$weight × $reps$dropdown" else "$variationPrefix/ × $reps$dropdown"
                    break
                }
            }
            _lastLogs.postValue(logsMap)
        }
    }

    override fun onCleared() {
        super.onCleared()
        exercises.removeObserver { _loadTrigger }
    }

    fun addExercise(name: String) {
        viewModelScope.launch {
            repository.addExercise(name, day)
        }
    }

    fun deleteExercise(exercise: Exercise) {
        viewModelScope.launch {
            repository.deleteExercise(exercise)
        }
    }

    fun saveOrder(exercises: List<Exercise>) {
        viewModelScope.launch {
            exercises.forEachIndexed { index, exercise ->
                repository.reorderExercise(exercise.id, index)
            }
        }
    }
}

class ExerciseListViewModelFactory(
    private val repository: WorkoutRepository,
    private val day: DayOfWeek
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ExerciseListViewModel(repository, day) as T
    }
}
