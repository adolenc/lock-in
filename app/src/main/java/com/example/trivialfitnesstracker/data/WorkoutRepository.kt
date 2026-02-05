package com.example.trivialfitnesstracker.data

import com.example.trivialfitnesstracker.data.dao.ExerciseDao
import com.example.trivialfitnesstracker.data.dao.ExerciseLogDao
import com.example.trivialfitnesstracker.data.dao.ExerciseVariationDao
import com.example.trivialfitnesstracker.data.dao.SetLogDao
import com.example.trivialfitnesstracker.data.dao.WorkoutSessionDao
import com.example.trivialfitnesstracker.data.entity.*
import java.time.Instant
import java.time.ZoneId

class WorkoutRepository(
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val setLogDao: SetLogDao,
    private val exerciseVariationDao: ExerciseVariationDao
) {
    // Exercise operations
    fun getExercisesForDay(day: DayOfWeek) = exerciseDao.getExercisesForDay(day)
    
    suspend fun getExercisesForDaySync(day: DayOfWeek) = exerciseDao.getExercisesForDaySync(day)

    suspend fun getExerciseById(id: Long) = exerciseDao.getById(id)

    suspend fun getExercisesByName(name: String) = exerciseDao.getByName(name)

    suspend fun addExercise(name: String, day: DayOfWeek): Long {
        val maxIndex = exerciseDao.getMaxOrderIndex(day) ?: -1
        return exerciseDao.insert(Exercise(name = name, dayOfWeek = day, orderIndex = maxIndex + 1))
    }

    suspend fun updateExercise(exercise: Exercise) = exerciseDao.update(exercise)

    suspend fun deleteExercise(exercise: Exercise) = exerciseDao.delete(exercise)

    suspend fun reorderExercise(exerciseId: Long, newIndex: Int) = 
        exerciseDao.updateOrderIndex(exerciseId, newIndex)

    // Workout session operations
    suspend fun startWorkoutSession(day: DayOfWeek): Long {
        return workoutSessionDao.insert(
            WorkoutSession(date = getAdjustedTime(), dayOfWeek = day)
        )
    }

    suspend fun getRecentSessionsForDay(day: DayOfWeek, limit: Int = 3) =
        workoutSessionDao.getRecentSessionsForDay(day, limit)

    // Exercise log operations
    suspend fun getOrCreateExerciseLog(sessionId: Long, exerciseId: Long): ExerciseLog {
        val existing = exerciseLogDao.getLogForSessionAndExercise(sessionId, exerciseId)
        if (existing != null) return existing
        
        val completedAt = getAdjustedTime()
        val newId = exerciseLogDao.insert(
            ExerciseLog(sessionId = sessionId, exerciseId = exerciseId, completedAt = completedAt)
        )
        return ExerciseLog(id = newId, sessionId = sessionId, exerciseId = exerciseId, completedAt = completedAt)
    }

    suspend fun getExerciseLogForSession(sessionId: Long, exerciseId: Long): ExerciseLog? =
        exerciseLogDao.getLogForSessionAndExercise(sessionId, exerciseId)

    suspend fun getRecentLogsForExercise(exerciseId: Long, limit: Int = 3) =
        exerciseLogDao.getRecentLogsForExercise(exerciseId, limit)

    suspend fun getAllLogsForExercises(exerciseIds: List<Long>) =
        exerciseLogDao.getAllLogsForExercises(exerciseIds)

    suspend fun updateExerciseNote(exerciseLogId: Long, note: String?) =
        exerciseLogDao.updateNote(exerciseLogId, note)

    suspend fun updateExerciseVariation(exerciseLogId: Long, variationId: Long?) =
        exerciseLogDao.updateVariation(exerciseLogId, variationId)

    suspend fun deleteExerciseLog(log: ExerciseLog) = exerciseLogDao.delete(log)
    
    // Variation operations
    suspend fun getVariationsForExercise(exerciseId: Long) = exerciseVariationDao.getVariationsForExercise(exerciseId)
    
    suspend fun getVariationById(id: Long) = exerciseVariationDao.getById(id)
    
    suspend fun addVariation(exerciseId: Long, name: String): Long {
        return exerciseVariationDao.insert(ExerciseVariation(exerciseId = exerciseId, name = name))
    }

    // Set log operations
    suspend fun logSet(exerciseLogId: Long, weight: Float?, reps: Int, isDropdown: Boolean = false): Long {
        val setNumber = if (isDropdown) {
            0 // dropdown sets don't have a number
        } else {
            (setLogDao.getMaxSetNumber(exerciseLogId) ?: 0) + 1
        }
        return setLogDao.insert(
            SetLog(exerciseLogId = exerciseLogId, setNumber = setNumber, weight = weight, reps = reps, isDropdown = isDropdown)
        )
    }

    suspend fun getSetsForExerciseLog(exerciseLogId: Long) = setLogDao.getSetsForExerciseLog(exerciseLogId)

    suspend fun deleteSetLog(setLogId: Long) = setLogDao.deleteById(setLogId)

    suspend fun updateSetsWeight(exerciseLogId: Long, weight: Float?) = 
        setLogDao.updateWeight(exerciseLogId, weight)

    private fun getAdjustedTime(): Long {
        val now = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        val zonedDateTime = Instant.ofEpochMilli(now).atZone(zoneId)

        if (zonedDateTime.hour < 4) {
            return zonedDateTime.minusDays(1).toInstant().toEpochMilli()
        }
        return now
    }
}
