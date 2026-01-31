package com.example.trivialfitnesstracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.trivialfitnesstracker.data.dao.ExerciseDao
import com.example.trivialfitnesstracker.data.dao.ExerciseLogDao
import com.example.trivialfitnesstracker.data.dao.SetLogDao
import com.example.trivialfitnesstracker.data.dao.WorkoutSessionDao
import com.example.trivialfitnesstracker.data.entity.Exercise
import com.example.trivialfitnesstracker.data.entity.ExerciseLog
import com.example.trivialfitnesstracker.data.entity.SetLog
import com.example.trivialfitnesstracker.data.entity.WorkoutSession

@Database(
    entities = [Exercise::class, WorkoutSession::class, ExerciseLog::class, SetLog::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun exerciseLogDao(): ExerciseLogDao
    abstract fun setLogDao(): SetLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fitness_tracker_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
