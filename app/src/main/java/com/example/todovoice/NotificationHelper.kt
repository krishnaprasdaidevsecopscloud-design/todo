package com.example.todovoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_ALARM = "voice_alarm_channel"
    const val CHANNEL_PENDING = "pending_digest_channel"
    const val CHANNEL_RINGING = "task_alarm_ringing_channel"
    const val CHANNEL_SYNC = "calendar_sync_channel"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ALARM, "Task Voice Alarm", NotificationManager.IMPORTANCE_HIGH)
            )
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_PENDING, "Pending Tasks Digest", NotificationManager.IMPORTANCE_DEFAULT)
            )
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_RINGING, "Task Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Rings at a task's time"
                    setSound(null, null) // AlarmRingService plays the chosen tone itself
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_SYNC, "Google Calendar Sync", NotificationManager.IMPORTANCE_DEFAULT)
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

    fun showMissedAlarm(context: Context, taskId: Long, title: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Missed alarm: $title")
            .setContentText("Open the app to update this task")
            .setContentIntent(openActivity(context, MainActivity::class.java))
            .setAutoCancel(true)
            .build()
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(4000 + taskId.toInt(), notification)
    }

    fun showCalendarReconnect(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("Reconnect Google Calendar")
            .setContentText("Calendar access expired. Tap to reconnect so events keep becoming tasks.")
            .setContentIntent(openActivity(context, CalendarSettingsActivity::class.java))
            .setAutoCancel(true)
            .build()
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(5001, notification)
    }

    private fun openActivity(context: Context, cls: Class<*>): PendingIntent =
        PendingIntent.getActivity(
            context, cls.name.hashCode(), Intent(context, cls),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
