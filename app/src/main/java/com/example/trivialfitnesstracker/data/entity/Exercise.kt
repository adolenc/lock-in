package com.example.trivialfitnesstracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dayOfWeek: DayOfWeek,
    val orderIndex: Int = 0
)
