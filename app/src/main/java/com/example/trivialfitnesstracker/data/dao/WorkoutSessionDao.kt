package com.example.trivialfitnesstracker.data.dao

import androidx.room.*
import com.example.trivialfitnesstracker.data.entity.DayOfWeek
import com.example.trivialfitnesstracker.data.entity.WorkoutSession
import com.example.trivialfitnesstracker.data.model.DailySetCount

@Dao
interface WorkoutSessionDao {
    @Query("""
        SELECT 
            ws.date, 
            COUNT(sl.id) as setCount 
        FROM workout_sessions ws 
        JOIN exercise_logs el ON ws.id = el.sessionId 
        JOIN set_logs sl ON el.id = sl.exerciseLogId 
        WHERE sl.isDropdown = 0 
        GROUP BY ws.id
    """)
    suspend fun getDailySetCounts(): List<DailySetCount>

    @Query("SELECT * FROM workout_sessions WHERE dayOfWeek = :day ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentSessionsForDay(day: DayOfWeek, limit: Int = 3): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getById(id: Long): WorkoutSession?

    @Query("SELECT * FROM workout_sessions ORDER BY date DESC LIMIT 1")
    suspend fun getMostRecent(): WorkoutSession?

    @Query("""
        SELECT DISTINCT ws.dayOfWeek
        FROM workout_sessions ws
        JOIN exercise_logs el ON ws.id = el.sessionId
        WHERE ws.date >= :startDate
    """)
    suspend fun getDaysWithCompletedExercisesSince(startDate: Long): List<DayOfWeek>

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Delete
    suspend fun delete(session: WorkoutSession)
}
