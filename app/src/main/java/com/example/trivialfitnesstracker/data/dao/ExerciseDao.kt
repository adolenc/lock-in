package com.example.trivialfitnesstracker.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.trivialfitnesstracker.data.entity.DayOfWeek
import com.example.trivialfitnesstracker.data.entity.Exercise

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE dayOfWeek = :day ORDER BY orderIndex ASC")
    fun getExercisesForDay(day: DayOfWeek): LiveData<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE dayOfWeek = :day ORDER BY orderIndex ASC")
    suspend fun getExercisesForDaySync(day: DayOfWeek): List<Exercise>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT * FROM exercises WHERE name = :name")
    suspend fun getByName(name: String): List<Exercise>

    @Query("SELECT MAX(orderIndex) FROM exercises WHERE dayOfWeek = :day")
    suspend fun getMaxOrderIndex(day: DayOfWeek): Int?

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Update
    suspend fun update(exercise: Exercise)

    @Delete
    suspend fun delete(exercise: Exercise)

    @Query("UPDATE exercises SET orderIndex = :newIndex WHERE id = :id")
    suspend fun updateOrderIndex(id: Long, newIndex: Int)
}
