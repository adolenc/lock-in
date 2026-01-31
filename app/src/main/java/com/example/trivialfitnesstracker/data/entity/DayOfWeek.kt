package com.example.trivialfitnesstracker.data.entity

enum class DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY;

    fun displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }
}
