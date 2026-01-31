package com.example.trivialfitnesstracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long, // epoch millis
    val dayOfWeek: DayOfWeek
)
