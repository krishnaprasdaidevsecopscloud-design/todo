package com.example.todovoice

import android.os.Bundle
import android.text.format.DateUtils as AndroidDateUtils
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Connect Google Calendar, choose the reminder lead time and the default alarm tone. */
class CalendarSettingsActivity : AppCompatActivity() {

    private val leadOptions = intArrayOf(0, 5, 10, 15, 30)

    private lateinit var statusText: TextView
    private lateinit var connectBtn: Button
    private lateinit var syncBtn: Button
    private lateinit var syncStatusText: TextView
    private lateinit var toneText: TextView

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            if (res.resultCode != RESULT_OK) {
                // the result intent usually carries Google's real reason; surface it instead of a generic toast
                val reason = try {
                    GoogleCalendarAuth.resultFromIntent(this, res.data)
                    null
                } catch (e: Exception) {
                    e
                }
                if (reason != null && !GoogleCalendarAuth.isCancel(reason)) showError(reason) else showNotGranted()
                return@registerForActivityResult
            }
            try {
                onAuthorized(GoogleCalendarAuth.resultFromIntent(this, res.data))
            } catch (e: Exception) {
                showError(e)
            }
        }

    private val tonePicker = AlarmTonePicker(this) { tone ->
        AppPrefs.setDefaultTone(this, tone)
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar_settings)

        statusText = findViewById(R.id.textCalendarStatus)
        connectBtn = findViewById(R.id.btnCalendarConnect)
        syncBtn = findViewById(R.id.btnSyncNow)
        syncStatusText = findViewById(R.id.textSyncStatus)
        toneText = findViewById(R.id.textDefaultTone)

        connectBtn.setOnClickListener {
            if (AppPrefs.isCalendarConnected(this)) confirmDisconnect() else connect()
        }
        syncBtn.setOnClickListener { CalendarSyncWorker.syncNow(this) }
        findViewById<Button>(R.id.btnDefaultTone).setOnClickListener {
            tonePicker.show(AppPrefs.getDefaultTone(this))
        }
        setUpLeadSpinner()

        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(CalendarSyncWorker.ONE_TIME_WORK)
            .observe(this) { infos -> showSyncState(infos.firstOrNull()) }

        refresh()
    }

    private fun setUpLeadSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerLead)
        val labels = leadOptions.map { if (it == 0) "At the event start" else "$it minutes before" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.setSelection(leadOptions.indexOf(AppPrefs.getLeadMinutes(this)).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val minutes = leadOptions[position]
                if (minutes == AppPrefs.getLeadMinutes(this@CalendarSettingsActivity)) return
                AppPrefs.setLeadMinutes(this@CalendarSettingsActivity, minutes)
                // re-time alarms of already-synced tasks
                if (AppPrefs.isCalendarConnected(this@CalendarSettingsActivity)) {
                    CalendarSyncWorker.syncNow(this@CalendarSettingsActivity)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun connect() {
        lifecycleScope.launch {
            try {
                val result = GoogleCalendarAuth.authorize(this@CalendarSettingsActivity)
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    onAuthorized(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    private fun onAuthorized(result: AuthorizationResult) {
        val account = result.toGoogleSignInAccount()?.email
        AppPrefs.setCalendarConnected(this, true, account)
        CalendarSyncWorker.schedulePeriodic(this)
        CalendarSyncWorker.syncNow(this)
        Toast.makeText(this, "Google Calendar connected", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun confirmDisconnect() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Disconnect Google Calendar?")
            .setMessage(
                "New events will stop becoming tasks. Tasks already created stay in your list.\n\n" +
                    "To fully remove the app's access, visit myaccount.google.com/permissions."
            )
            .setPositiveButton("Disconnect") { _, _ ->
                AppPrefs.setCalendarConnected(this, false, null)
                CalendarSyncWorker.cancel(this)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSyncState(info: WorkInfo?) {
        when (info?.state) {
            WorkInfo.State.ENQUEUED -> syncStatusText.text = "Waiting for internet…"
            WorkInfo.State.RUNNING -> syncStatusText.text = "Syncing…"
            WorkInfo.State.FAILED -> {
                syncStatusText.text = "Last sync failed: " +
                    (info.outputData.getString(CalendarSyncWorker.KEY_ERROR) ?: "unknown error")
                refreshConnection()
            }
            else -> refresh()
        }
    }

    private fun refresh() {
        refreshConnection()
        val lastSync = AppPrefs.getLastSync(this)
        syncStatusText.text = if (lastSync == 0L) "Never synced"
        else "Last synced " + AndroidDateUtils.getRelativeTimeSpanString(lastSync)
        toneText.text = AlarmTonePicker.displayName(this, AppPrefs.getDefaultTone(this), "System alarm sound")
    }

    private fun refreshConnection() {
        val connected = AppPrefs.isCalendarConnected(this)
        val account = AppPrefs.getCalendarAccount(this)
        statusText.text = when {
            connected && account != null -> "Connected as $account"
            connected -> "Connected"
            else -> "Not connected"
        }
        connectBtn.text = if (connected) "Disconnect" else "Connect Google Calendar"
        syncBtn.isEnabled = connected
    }

    private fun showError(e: Exception) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Couldn't connect Google Calendar")
            .setMessage(GoogleCalendarAuth.describeError(e))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showNotGranted() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Google Calendar access was not granted")
            .setMessage(GoogleCalendarAuth.NOT_GRANTED_HELP)
            .setPositiveButton("OK", null)
            .show()
    }
}
