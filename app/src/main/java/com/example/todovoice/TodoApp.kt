package com.example.todovoice

import android.app.Application

class TodoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        PendingReminderWorker.schedule(this) // every 3 hours, survives process death
    }
}
