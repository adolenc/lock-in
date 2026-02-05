package com.example.trivialfitnesstracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.trivialfitnesstracker.data.entity.ExerciseVariation

@Dao
interface ExerciseVariationDao {
    @Query("SELECT * FROM exercise_variations WHERE exerciseId = :exerciseId ORDER BY name ASC")
    suspend fun getVariationsForExercise(exerciseId: Long): List<ExerciseVariation>

    @Query("SELECT * FROM exercise_variations WHERE id = :id")
    suspend fun getById(id: Long): ExerciseVariation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(variation: ExerciseVariation): Long
}
