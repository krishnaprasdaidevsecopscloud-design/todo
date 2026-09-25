package com.example.todovoice

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        requestNotificationPermissionIfNeeded()
        if (savedInstanceState == null) {
            checkAlarmPermissions()
            if (AppPrefs.isCalendarConnected(this)) CalendarSyncWorker.syncNow(this)
        }

        showFragment(ListMode.ACTIVE)

        val tabs = findViewById<TabLayout>(R.id.tabLayout)
        tabs.addTab(tabs.newTab().setText("Tasks"))
        tabs.addTab(tabs.newTab().setText("Pending"))
        tabs.addTab(tabs.newTab().setText("Completed"))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> showFragment(ListMode.ACTIVE)
                    1 -> showFragment(ListMode.PENDING)
                    2 -> showFragment(ListMode.COMPLETED)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_calendar_settings) {
            startActivity(Intent(this, CalendarSettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showFragment(mode: ListMode) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, TaskListFragment.newInstance(mode))
            .commit()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    /**
     * Task alarms need "Alarms & reminders" (exact time) and, on Android 14+,
     * full-screen notifications (alarm screen over the lock screen). Asks once.
     */
    private fun checkAlarmPermissions() {
        if (AppPrefs.wasAlarmPermissionAsked(this)) return
        val packageUri = Uri.parse("package:$packageName")

        val (message, settingsIntent) = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !TaskAlarmScheduler.canScheduleExact(this) ->
                "To ring at the exact task time, allow \"Alarms & reminders\" for TodoVoice." to
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !getSystemService(NotificationManager::class.java).canUseFullScreenIntent() ->
                "To show the alarm screen over the lock screen, allow full-screen notifications for TodoVoice." to
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
            else -> return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Allow task alarms")
            .setMessage(message)
            .setPositiveButton("Open settings") { _, _ ->
                try {
                    startActivity(settingsIntent)
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
                }
            }
            .setNegativeButton("Not now") { _, _ -> AppPrefs.setAlarmPermissionAsked(this) }
            .show()
    }
}
