package com.example.todovoice

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): TaskStatus = TaskStatus.valueOf(value)
}

@Database(entities = [Task::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v2: task time (remindAt), per-task alarm tone, Google Calendar event link
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN remindAt INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN alarmTone TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN calendarEventId TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_tasks_calendarEventId ON tasks (calendarEventId)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "todo_voice_db"
                ).addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
