package com.example.trivialfitnesstracker.ui.workout

import androidx.lifecycle.*
import com.example.trivialfitnesstracker.data.WorkoutRepository
import com.example.trivialfitnesstracker.data.entity.Exercise
import com.example.trivialfitnesstracker.data.entity.SetLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ExerciseHistory(
    val date: String,
    val sets: List<SetLog>,
    val note: String?
)

class WorkoutPageViewModel(
    private val repository: WorkoutRepository,
    private val sessionId: Long,
    private val exerciseId: Long
) : ViewModel() {

    private val _exercise = MutableLiveData<Exercise>()
    val exercise: LiveData<Exercise> = _exercise

    private val _history = MutableLiveData<List<ExerciseHistory>>()
    val history: LiveData<List<ExerciseHistory>> = _history

    private val _todaySets = MutableLiveData<List<SetLog>>()
    val todaySets: LiveData<List<SetLog>> = _todaySets

    private val _lastReps = MutableLiveData<Int?>()
    val lastReps: LiveData<Int?> = _lastReps

    private val _lastWeight = MutableLiveData<Float?>()
    val lastWeight: LiveData<Float?> = _lastWeight

    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> = _canUndo

    private val _currentNote = MutableLiveData<String?>()
    val currentNote: LiveData<String?> = _currentNote

    private var lastLoggedSetId: Long? = null

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val ex = repository.getExerciseById(exerciseId) ?: return@launch
            _exercise.value = ex

            loadHistory()
            
            // Get last weight and reps (from initial history load)
            // Note: If we want this to update dynamically, we should move it to loadHistory or observe history
            // But usually initial load is fine.
            val historyList = _history.value ?: emptyList()
            val lastSets = historyList.firstOrNull()?.sets?.filter { !it.isDropdown }
            _lastWeight.value = lastSets?.firstOrNull()?.weight
            _lastReps.value = lastSets?.firstOrNull()?.reps

            // Load today's sets
            loadTodaySets()
            
            // Load note
            val exerciseLog = repository.getExerciseLogForSession(sessionId, exerciseId)
            _currentNote.value = exerciseLog?.note
        }
    }

    private suspend fun loadHistory() {
        val recentLogs = repository.getRecentLogsForExercise(exerciseId, 3)
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val historyList = recentLogs.map { log ->
            val sets = repository.getSetsForExerciseLog(log.id)
            ExerciseHistory(
                date = dateFormat.format(Date(log.completedAt)),
                sets = sets,
                note = log.note
            )
        }
        _history.value = historyList
    }

    private suspend fun loadTodaySets() {
        val exerciseLog = repository.getOrCreateExerciseLog(sessionId, exerciseId)
        val sets = repository.getSetsForExerciseLog(exerciseLog.id)
        _todaySets.value = sets
    }

    fun logSet(weight: Float?, reps: Int) {
        viewModelScope.launch {
            val exerciseLog = repository.getOrCreateExerciseLog(sessionId, exerciseId)
            lastLoggedSetId = repository.logSet(exerciseLog.id, weight, reps, isDropdown = false)
            _canUndo.value = true
            loadTodaySets()
            loadHistory() // Refresh history in case current session is displayed there
        }
    }

    fun logDropdown(reps: Int) {
        viewModelScope.launch {
            val exerciseLog = repository.getOrCreateExerciseLog(sessionId, exerciseId)
            lastLoggedSetId = repository.logSet(exerciseLog.id, null, reps, isDropdown = true)
            _canUndo.value = true
            loadTodaySets()
            loadHistory()
        }
    }

    fun undoLastSet() {
        viewModelScope.launch {
            val setId = lastLoggedSetId ?: return@launch
            repository.deleteSetLog(setId)
            lastLoggedSetId = null
            _canUndo.value = false
            loadTodaySets()
            loadHistory()
        }
    }

    fun setNote(note: String?) {
        viewModelScope.launch {
            val exerciseLog = repository.getOrCreateExerciseLog(sessionId, exerciseId)
            val trimmedNote = note?.trim()?.ifEmpty { null }
            repository.updateExerciseNote(exerciseLog.id, trimmedNote)
            _currentNote.value = trimmedNote
        }
    }

    fun updateWeight(newWeight: Float?) {
        viewModelScope.launch {
            val exerciseLog = repository.getExerciseLogForSession(sessionId, exerciseId) ?: return@launch
            repository.updateSetsWeight(exerciseLog.id, newWeight)
            // Refresh data to reflect weight change in UI
            loadTodaySets()
            loadHistory()
        }
    }
}

class WorkoutPageViewModelFactory(
    private val repository: WorkoutRepository,
    private val sessionId: Long,
    private val exerciseId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return WorkoutPageViewModel(repository, sessionId, exerciseId) as T
    }
}
