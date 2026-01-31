package com.example.trivialfitnesstracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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
        
        const val DATABASE_NAME = "fitness_tracker_db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE exercise_logs ADD COLUMN note TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
