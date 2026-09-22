package com.example.todovoice

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Runs every 3 hours. Pulls the current list of PENDING tasks and
 * pushes a notification (and speaks a short summary) so pending work
 * never silently falls off the radar.
 */
class PendingReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = TaskRepository.getInstance(applicationContext)
        val pending = repo.getPendingSnapshot()
        if (pending.isNotEmpty()) {
            NotificationHelper.showPendingDigest(applicationContext, pending)
            speakSummary(pending)
        }
        return Result.success()
    }

    private fun speakSummary(tasks: List<Task>) {
        var tts: TextToSpeech? = null
        val text = "You have ${tasks.size} pending tasks: " +
                tasks.joinToString(", ") { it.title }
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pending_digest")
            }
        }
    }

    companion object {
        private const val WORK_NAME = "pending_reminder_work"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PendingReminderWorker>(3, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
