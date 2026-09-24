package com.example.todovoice

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Small app-wide settings: default alarm tone and Google Calendar sync state. */
object AppPrefs {
    private const val FILE = "todo_voice_prefs"
    private const val KEY_DEFAULT_TONE = "default_tone"
    private const val KEY_CAL_CONNECTED = "calendar_connected"
    private const val KEY_CAL_ACCOUNT = "calendar_account"
    private const val KEY_LEAD_MINUTES = "calendar_lead_minutes"
    private const val KEY_LAST_SYNC = "calendar_last_sync"
    private const val KEY_ALARM_PERMS_ASKED = "alarm_perms_asked"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Tone Uri used when a task has none; null = system alarm sound. */
    fun getDefaultTone(context: Context): String? = prefs(context).getString(KEY_DEFAULT_TONE, null)
    fun setDefaultTone(context: Context, tone: String?) = prefs(context).edit { putString(KEY_DEFAULT_TONE, tone) }

    fun isCalendarConnected(context: Context) = prefs(context).getBoolean(KEY_CAL_CONNECTED, false)
    fun getCalendarAccount(context: Context): String? = prefs(context).getString(KEY_CAL_ACCOUNT, null)
    fun setCalendarConnected(context: Context, connected: Boolean, account: String?) = prefs(context).edit {
        putBoolean(KEY_CAL_CONNECTED, connected)
        putString(KEY_CAL_ACCOUNT, account)
    }

    /** How many minutes before a calendar event its task alarm rings. */
    fun getLeadMinutes(context: Context) = prefs(context).getInt(KEY_LEAD_MINUTES, 10)
    fun setLeadMinutes(context: Context, minutes: Int) = prefs(context).edit { putInt(KEY_LEAD_MINUTES, minutes) }

    fun getLastSync(context: Context) = prefs(context).getLong(KEY_LAST_SYNC, 0L)
    fun setLastSync(context: Context, millis: Long) = prefs(context).edit { putLong(KEY_LAST_SYNC, millis) }

    fun wasAlarmPermissionAsked(context: Context) = prefs(context).getBoolean(KEY_ALARM_PERMS_ASKED, false)
    fun setAlarmPermissionAsked(context: Context) = prefs(context).edit { putBoolean(KEY_ALARM_PERMS_ASKED, true) }
}
