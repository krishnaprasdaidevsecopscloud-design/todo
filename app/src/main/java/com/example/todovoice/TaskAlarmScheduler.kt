package com.example.todovoice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules the one-shot "task time has come" alarm. Uses setAlarmClock so it
 * fires exactly, even in Doze, and shows the alarm icon in the status bar.
 */
object TaskAlarmScheduler {

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, taskId: Long, atMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val firePi = firePendingIntent(context, taskId)
        if (!canScheduleExact(context)) {
            // user revoked "Alarms & reminders": still ring, just possibly a few minutes late
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, firePi)
            return
        }
        val showPi = PendingIntent.getActivity(
            context, taskId.toInt(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, showPi), firePi)
    }

    fun cancel(context: Context, taskId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(firePendingIntent(context, taskId))
    }

    private fun firePendingIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, DueAlarmReceiver::class.java).apply {
            putExtra(DueAlarmReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
