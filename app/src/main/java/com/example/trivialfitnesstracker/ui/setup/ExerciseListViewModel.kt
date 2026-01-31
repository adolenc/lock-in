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
