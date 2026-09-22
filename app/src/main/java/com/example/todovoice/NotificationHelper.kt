package com.example.todovoice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_ALARM = "voice_alarm_channel"
    const val CHANNEL_PENDING = "pending_digest_channel"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ALARM, "Task Voice Alarm", NotificationManager.IMPORTANCE_HIGH)
            )
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_PENDING, "Pending Tasks Digest", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun showPendingDigest(context: Context, tasks: List<Task>) {
        if (tasks.isEmpty()) return
        val lines = tasks.joinToString("\n") { "- ${it.title} (due ${android.text.format.DateFormat.format("dd MMM", it.pendingUntil ?: it.dueDate)})" }
        val notification = NotificationCompat.Builder(context, CHANNEL_PENDING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("You have ${tasks.size} pending task(s)")
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
            .setAutoCancel(true)
            .build()
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(2001, notification)
    }

    fun showAlarmNotification(context: Context, task: Task) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Still in progress: ${task.title}")
            .setContentText("Update the status in the app to stop the reminder")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(1000 + task.id.toInt(), notification)
    }
}
