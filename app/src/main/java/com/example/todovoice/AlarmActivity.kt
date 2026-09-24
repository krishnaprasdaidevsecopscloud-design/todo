package com.example.todovoice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/** Full-screen alarm shown over the lock screen when a task's time arrives. */
class AlarmActivity : AppCompatActivity() {

    private var taskId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContentView(R.layout.activity_alarm)
        bind(intent)

        findViewById<Button>(R.id.btnAlarmSnooze).text = "Snooze ${AlarmRingService.SNOOZE_MINUTES} min"
        findViewById<Button>(R.id.btnAlarmSnooze).setOnClickListener { send(AlarmRingService.ACTION_SNOOZE) }
        findViewById<Button>(R.id.btnAlarmStart).setOnClickListener { send(AlarmRingService.ACTION_START) }
        findViewById<Button>(R.id.btnAlarmDismiss).setOnClickListener { send(AlarmRingService.ACTION_DISMISS) }

        // close when the alarm stops some other way (notification action, timeout, another alarm)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AlarmRingService.ringingTaskId.collect { ringing ->
                    if (ringing != taskId) finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
    }

    private fun bind(intent: Intent) {
        taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        findViewById<TextView>(R.id.textAlarmTime).text = DateUtils.formatTime(this, System.currentTimeMillis())
        findViewById<TextView>(R.id.textAlarmTitle).text = intent.getStringExtra(EXTRA_TITLE)
        val note = intent.getStringExtra(EXTRA_NOTE)
        findViewById<TextView>(R.id.textAlarmNote).apply {
            text = note
            visibility = if (note.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun send(action: String) {
        startService(AlarmRingService.actionIntent(this, action, taskId))
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val EXTRA_TASK_ID = "extra_task_id"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_NOTE = "extra_note"

        fun intent(context: Context, taskId: Long, title: String, note: String?): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_NOTE, note)
            }
    }
}
