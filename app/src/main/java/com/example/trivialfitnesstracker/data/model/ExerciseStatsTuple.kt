package com.example.trivialfitnesstracker.data.model

data class ExerciseStatsTuple(
    val exerciseId: Long,
    val exerciseName: String,
    val dayName: String,
    val orderIndex: Int,
    val date: Long,
    val totalWeight: Float
)
