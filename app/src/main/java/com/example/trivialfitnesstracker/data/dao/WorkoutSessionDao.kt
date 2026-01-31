package com.example.trivialfitnesstracker.data.dao

import androidx.room.*
import com.example.trivialfitnesstracker.data.entity.DayOfWeek
import com.example.trivialfitnesstracker.data.entity.WorkoutSession

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions WHERE dayOfWeek = :day ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentSessionsForDay(day: DayOfWeek, limit: Int = 3): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getById(id: Long): WorkoutSession?

    @Query("SELECT * FROM workout_sessions ORDER BY date DESC LIMIT 1")
    suspend fun getMostRecent(): WorkoutSession?

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Delete
    suspend fun delete(session: WorkoutSession)
}
