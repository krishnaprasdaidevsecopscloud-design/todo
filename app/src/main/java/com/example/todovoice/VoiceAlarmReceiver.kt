package com.example.todovoice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Fires every REPEAT_INTERVAL_MS while a task is IN_PROGRESS.
 * Speaks a reminder out loud and re-checks the DB: if the task is no
 * longer IN_PROGRESS (user updated status), it stops rescheduling itself.
 */
class VoiceAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId == -1L) return

        val repo = TaskRepository.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            val task = repo.getById(taskId) ?: return@launch
            if (task.status != TaskStatus.IN_PROGRESS) {
                // status changed -> stop the loop, nothing else to do
                return@launch
            }

            NotificationHelper.showAlarmNotification(context, task)
            speak(context, "Task ${task.title} is still in progress. Please update its status.")

            // reschedule next alarm
            scheduleNext(context, taskId)
        }
    }

    private fun speak(context: Context, text: String) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_alarm")
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val REPEAT_INTERVAL_MS = 5 * 60 * 1000L // every 5 minutes until updated

        fun schedule(context: Context, taskId: Long) {
            scheduleAt(context, taskId, System.currentTimeMillis() + REPEAT_INTERVAL_MS)
        }

        fun scheduleNext(context: Context, taskId: Long) {
            scheduleAt(context, taskId, System.currentTimeMillis() + REPEAT_INTERVAL_MS)
        }

        fun cancel(context: Context, taskId: Long) {
            val pi = pendingIntentFor(context, taskId)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pi)
        }

        private fun scheduleAt(context: Context, taskId: Long, atMillis: Long) {
            val pi = pendingIntentFor(context, taskId)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }

        private fun pendingIntentFor(context: Context, taskId: Long): PendingIntent {
            val intent = Intent(context, VoiceAlarmReceiver::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
            }
            return PendingIntent.getBroadcast(
                context, taskId.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
