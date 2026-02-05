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
import com.example.trivialfitnesstracker.data.dao.ExerciseVariationDao
import com.example.trivialfitnesstracker.data.dao.SetLogDao
import com.example.trivialfitnesstracker.data.dao.WorkoutSessionDao
import com.example.trivialfitnesstracker.data.entity.Exercise
import com.example.trivialfitnesstracker.data.entity.ExerciseLog
import com.example.trivialfitnesstracker.data.entity.ExerciseVariation
import com.example.trivialfitnesstracker.data.entity.SetLog
import com.example.trivialfitnesstracker.data.entity.WorkoutSession

@Database(
    entities = [Exercise::class, WorkoutSession::class, ExerciseLog::class, SetLog::class, ExerciseVariation::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun exerciseLogDao(): ExerciseLogDao
    abstract fun setLogDao(): SetLogDao
    abstract fun exerciseVariationDao(): ExerciseVariationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        const val DATABASE_NAME = "fitness_tracker_db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE exercise_logs ADD COLUMN note TEXT")
            }
        }
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create ExerciseVariation table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exercise_variations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `exerciseId` INTEGER NOT NULL, 
                        `name` TEXT NOT NULL, 
                        FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_variations_exerciseId` ON `exercise_variations` (`exerciseId`)")
                
                // Add column to exercise_logs (without FK constraint first because SQLite limitation)
                // Actually adding column with FK requires recreating table in SQLite usually, 
                // but we can just add the column and ignore FK check for existing data if we are careful.
                // Or better, recreate the table. 
                // However, "ALTER TABLE ADD COLUMN" supports REFERENCES in newer SQLite, but Room might be strict.
                // Let's try simple add column. If Room validation fails, we might need complex migration.
                // Given this is a prototype/CLI environment, we can try lightweight approach.
                // But strict FK support usually requires table recreation.
                // For simplicity, let's just add the column. Room verifies schema on open.
                
                // Note: Adding a column with FK constraint is not directly supported by standard SQLite ALTER TABLE in all versions/modes.
                // But we can add the column. 
                database.execSQL("ALTER TABLE exercise_logs ADD COLUMN variationId INTEGER DEFAULT NULL REFERENCES exercise_variations(id) ON DELETE SET NULL")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_logs_variationId` ON `exercise_logs` (`variationId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
