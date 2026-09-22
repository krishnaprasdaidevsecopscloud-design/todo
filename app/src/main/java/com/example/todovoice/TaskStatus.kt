package com.example.todovoice

enum class TaskStatus {
    TODO,          // freshly created, not yet acted on
    IN_PROGRESS,   // being worked on today, triggers repeating voice alarm
    PENDING,       // pushed to a future date, shows in Pending area
    COMPLETED      // done, timestamped
}
