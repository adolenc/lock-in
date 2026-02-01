package com.example.trivialfitnesstracker.data.dao

import androidx.room.*
import com.example.trivialfitnesstracker.data.entity.ExerciseLog
import com.example.trivialfitnesstracker.data.entity.SetLog

@Dao
interface ExerciseLogDao {
    @Query("SELECT * FROM exercise_logs WHERE sessionId = :sessionId ORDER BY completedAt ASC")
    suspend fun getLogsForSession(sessionId: Long): List<ExerciseLog>

    @Query("SELECT * FROM exercise_logs WHERE exerciseId = :exerciseId ORDER BY completedAt DESC LIMIT :limit")
    suspend fun getRecentLogsForExercise(exerciseId: Long, limit: Int = 3): List<ExerciseLog>

    @Query("SELECT * FROM exercise_logs WHERE exerciseId IN (:exerciseIds) ORDER BY completedAt DESC")
    suspend fun getAllLogsForExercises(exerciseIds: List<Long>): List<ExerciseLog>

    @Query("SELECT * FROM exercise_logs WHERE sessionId = :sessionId AND exerciseId = :exerciseId LIMIT 1")
    suspend fun getLogForSessionAndExercise(sessionId: Long, exerciseId: Long): ExerciseLog?

    @Insert
    suspend fun insert(log: ExerciseLog): Long

    @Delete
    suspend fun delete(log: ExerciseLog)

    @Query("UPDATE exercise_logs SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)
}

@Dao
interface SetLogDao {
    @Query("SELECT * FROM set_logs WHERE exerciseLogId = :exerciseLogId ORDER BY setNumber ASC")
    suspend fun getSetsForExerciseLog(exerciseLogId: Long): List<SetLog>

    @Query("SELECT MAX(setNumber) FROM set_logs WHERE exerciseLogId = :exerciseLogId AND isDropdown = 0")
    suspend fun getMaxSetNumber(exerciseLogId: Long): Int?

    @Insert
    suspend fun insert(setLog: SetLog): Long

    @Delete
    suspend fun delete(setLog: SetLog)

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE set_logs SET weight = :weight WHERE exerciseLogId = :exerciseLogId AND isDropdown = 0")
    suspend fun updateWeight(exerciseLogId: Long, weight: Float?)
}
