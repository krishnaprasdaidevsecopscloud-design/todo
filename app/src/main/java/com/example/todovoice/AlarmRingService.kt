package com.example.todovoice

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Rings a task alarm: loops the chosen tone on the alarm stream, speaks the
 * task title, and shows a full-screen notification (AlarmActivity) with
 * Snooze / Start / Dismiss. Stops by itself after RING_DURATION_MS.
 */
class AlarmRingService : Service() {

    private data class Ringing(val taskId: Long, val title: String)

    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { onTimeout() }
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var current: Ringing? = null

    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L
        when (intent?.action) {
            ACTION_RING -> ring(intent, taskId)
            ACTION_DISMISS -> stopRinging(taskId)
            ACTION_SNOOZE -> {
                if (taskId != -1L) {
                    TaskAlarmScheduler.schedule(this, taskId, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L)
                }
                stopRinging(taskId)
            }
            ACTION_START -> {
                stopRinging(taskId)
                val appContext = applicationContext
                CoroutineScope(Dispatchers.IO).launch {
                    val repo = TaskRepository.getInstance(appContext)
                    val task = repo.getById(taskId) ?: return@launch
                    repo.markInProgress(task)
                    VoiceAlarmReceiver.schedule(appContext, task.id)
                }
            }
            else -> if (current == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ring(intent: Intent, taskId: Long) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Task"
        val note = intent.getStringExtra(EXTRA_NOTE)
        val tone = intent.getStringExtra(EXTRA_TONE)

        // a second alarm arriving while one rings replaces it; don't lose the first silently
        current?.takeIf { it.taskId != taskId }?.let {
            NotificationHelper.showMissedAlarm(this, it.taskId, it.title)
        }

        current = Ringing(taskId, title)
        _ringingTaskId.value = taskId

        // must promote to foreground right away; posting this notification launches AlarmActivity
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(taskId, title, note), foregroundType())

        startTone(tone)
        speak("Time for your task: $title")

        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, RING_DURATION_MS)
    }

    private fun startTone(tone: String?) {
        stopTone()
        // chosen tone, else system alarm, else ringtone (a deleted file or lost permission must not mean silence)
        val candidates = listOfNotNull(
            tone?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(alarmAttributes)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (e: Exception) {
                mp.release()
            }
        }
    }

    private fun speak(text: String) {
        tts?.shutdown()
        var engine: TextToSpeech? = null
        engine = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
                engine?.setAudioAttributes(alarmAttributes)
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) = restoreToneVolume()
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = restoreToneVolume()
                })
                player?.setVolume(0.2f, 0.2f) // duck the tone so the words are audible
                engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "task_alarm")
            }
        }
        tts = engine
    }

    private fun restoreToneVolume() {
        handler.post { player?.setVolume(1f, 1f) }
    }

    private fun onTimeout() {
        current?.let { NotificationHelper.showMissedAlarm(this, it.taskId, it.title) }
        stopAll()
    }

    private fun stopRinging(taskId: Long) {
        val ringing = current
        if (ringing == null || ringing.taskId == taskId) stopAll()
    }

    private fun stopAll() {
        releaseAudio()
        current = null
        _ringingTaskId.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopTone() {
        player?.run {
            try { stop() } catch (_: IllegalStateException) {}
            release()
        }
        player = null
    }

    private fun releaseAudio() {
        handler.removeCallbacks(timeout)
        stopTone()
        tts?.run { stop(); shutdown() }
        tts = null
    }

    override fun onDestroy() {
        releaseAudio()
        _ringingTaskId.value = null
        super.onDestroy()
    }

    private fun buildNotification(taskId: Long, title: String, note: String?): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, taskId.toInt(), AlarmActivity.intent(this, taskId, title, note),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ $title")
            .setContentText(note ?: "It's time for this task")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Snooze ${SNOOZE_MINUTES}m", actionPendingIntent(ACTION_SNOOZE, taskId))
            .addAction(0, "Start", actionPendingIntent(ACTION_START, taskId))
            .addAction(0, "Dismiss", actionPendingIntent(ACTION_DISMISS, taskId))
            .build()
    }

    private fun actionPendingIntent(action: String, taskId: Long): PendingIntent =
        PendingIntent.getService(
            this, (action.hashCode() * 31 + taskId).toInt(), actionIntent(this, action, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

    companion object {
        const val ACTION_RING = "com.example.todovoice.action.RING"
        const val ACTION_DISMISS = "com.example.todovoice.action.DISMISS"
        const val ACTION_SNOOZE = "com.example.todovoice.action.SNOOZE"
        const val ACTION_START = "com.example.todovoice.action.START"
        const val SNOOZE_MINUTES = 10

        private const val EXTRA_TASK_ID = "extra_task_id"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_NOTE = "extra_note"
        private const val EXTRA_TONE = "extra_tone"
        private const val NOTIFICATION_ID = 3001
        private const val RING_DURATION_MS = 3 * 60 * 1000L // stop ringing after 3 minutes

        private val _ringingTaskId = MutableStateFlow<Long?>(null)
        /** Task currently ringing, or null; AlarmActivity closes itself when this changes. */
        val ringingTaskId: StateFlow<Long?> = _ringingTaskId

        fun ringIntent(context: Context, task: Task, tone: String?): Intent =
            Intent(context, AlarmRingService::class.java).apply {
                action = ACTION_RING
                putExtra(EXTRA_TASK_ID, task.id)
                putExtra(EXTRA_TITLE, task.title)
                putExtra(EXTRA_NOTE, task.note)
                putExtra(EXTRA_TONE, tone)
            }

        fun actionIntent(context: Context, action: String, taskId: Long): Intent =
            Intent(context, AlarmRingService::class.java).apply {
                this.action = action
                putExtra(EXTRA_TASK_ID, taskId)
            }
    }
}
