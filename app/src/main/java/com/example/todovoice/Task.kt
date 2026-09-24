package com.example.todovoice

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["calendarEventId"], unique = true)]
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String,                 // "why am I creating this task" reminder note
    val dueDate: Long,                 // epoch millis, start-of-day the task is scheduled for
    val appLink: String? = null,       // optional deep link opened when task row is tapped
    val status: TaskStatus = TaskStatus.TODO,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,     // set when marked COMPLETED
    val pendingUntil: Long? = null,    // new target date when marked PENDING
    val remindAt: Long? = null,        // exact alarm time (epoch millis); null = date only, no alarm
    val alarmTone: String? = null,     // alarm tone Uri; null = app default tone
    val calendarEventId: String? = null // Google Calendar event id for auto-created tasks
)
