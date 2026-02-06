package com.example.trivialfitnesstracker.ui.workout

import androidx.lifecycle.*
import com.example.trivialfitnesstracker.data.WorkoutRepository
import com.example.trivialfitnesstracker.data.entity.Exercise
import com.example.trivialfitnesstracker.data.entity.SetLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import com.example.trivialfitnesstracker.data.entity.ExerciseVariation

data class ExerciseHistory(
    val date: String,
    val sets: List<SetLog>,
    val note: String?,
    val variation: String? = null
)

class WorkoutPageViewModel(
    private val repository: WorkoutRepository,
    private val sessionId: Long,
    private val exerciseId: Long
) : ViewModel() {

    private val _exercise = MutableLiveData<Exercise>()
    val exercise: LiveData<Exercise> = _exercise

    private val _variations = MutableLiveData<List<ExerciseVariation>>()
    val variations: LiveData<List<ExerciseVariation>> = _variations

    private val _selectedVariation = MutableLiveData<ExerciseVariation?>()
    val selectedVariation: LiveData<ExerciseVariation?> = _selectedVariation

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
            
            // Load note and variation
            val exerciseLog = repository.getExerciseLogForSession(sessionId, exerciseId)
            _currentNote.value = exerciseLog?.note
            
            // Load variations list
            loadVariations()
            
            // Set selected variation from log
            if (exerciseLog?.variationId != null) {
                val variation = repository.getVariationById(exerciseLog.variationId)
                _selectedVariation.value = variation
            } else {
                // Auto-select based on most recent history log if not set for current session
                val recentLogs = repository.getRecentLogsForExercise(exerciseId, 5)
                val lastLog = recentLogs.firstOrNull { it.sessionId != sessionId }
                
                if (lastLog?.variationId != null) {
                    val variation = repository.getVariationById(lastLog.variationId)
                    _selectedVariation.value = variation
                    
                    if (variation != null) {
                        setVariation(variation)
                    }
                } else {
                    _selectedVariation.value = null
                }
            }
        }
    }

    private suspend fun loadVariations() {
        val list = repository.getVariationsForExercise(exerciseId)
        _variations.value = list
    }

    private suspend fun loadHistory() {
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        var history: ExerciseHistory? = null
        val recentLogs = repository.getRecentLogsForExercise(exerciseId, 10)
        for (log in recentLogs) {
            if (log.sessionId == sessionId) continue
            val sets = repository.getSetsForExerciseLog(log.id)
            if (sets.isEmpty()) continue
            val variation = if (log.variationId != null) repository.getVariationById(log.variationId)?.name else null
            history = ExerciseHistory(
                date = dateFormat.format(Date(log.completedAt)),
                sets = sets,
                note = log.note,
                variation = variation
            )
            break
        }
        _history.value = history?.let { listOf(it) } ?: emptyList()
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

    fun setVariation(variation: ExerciseVariation?) {
        viewModelScope.launch {
            val exerciseLog = repository.getOrCreateExerciseLog(sessionId, exerciseId)
            repository.updateExerciseVariation(exerciseLog.id, variation?.id)
            _selectedVariation.value = variation
        }
    }

    fun addVariation(name: String) {
        viewModelScope.launch {
            val id = repository.addVariation(exerciseId, name)
            loadVariations() // Refresh list
            
            // Automatically select the new variation
            val newVariation = repository.getVariationById(id)
            setVariation(newVariation)
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
