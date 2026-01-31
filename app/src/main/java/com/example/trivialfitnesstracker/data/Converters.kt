package com.example.trivialfitnesstracker.data

import androidx.room.TypeConverter
import com.example.trivialfitnesstracker.data.entity.DayOfWeek

class Converters {
    @TypeConverter
    fun fromDayOfWeek(day: DayOfWeek): String = day.name

    @TypeConverter
    fun toDayOfWeek(value: String): DayOfWeek = DayOfWeek.valueOf(value)
}
