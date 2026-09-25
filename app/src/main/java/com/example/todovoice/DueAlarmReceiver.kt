package com.example.todovoice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fired by TaskAlarmScheduler at the task's time. Looks the task up and, if it
 * still needs doing, starts AlarmRingService (tone + voice + full-screen alert).
 */
class DueAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId == -1L) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = TaskRepository.getInstance(context).getById(taskId) ?: return@launch
                // already started or done -> nothing to ring for
                if (task.status != TaskStatus.TODO && task.status != TaskStatus.PENDING) return@launch

                val tone = task.alarmTone ?: AppPrefs.getDefaultTone(context)
                try {
                    ContextCompat.startForegroundService(
                        context, AlarmRingService.ringIntent(context, task, tone)
                    )
                } catch (e: IllegalStateException) {
                    // Android 12+ refuses background service starts from inexact alarms
                    // (exact-alarm permission revoked); at least notify.
                    NotificationHelper.showMissedAlarm(context, task.id, task.title)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
