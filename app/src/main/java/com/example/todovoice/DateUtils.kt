package com.example.todovoice

import android.content.Context
import java.util.Calendar
import java.util.Date

object DateUtils {
    fun startOfDay(millis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun isToday(millis: Long): Boolean = startOfDay(millis) == startOfDay()

    /** [dayMillis] at the given wall-clock time. */
    fun atTime(dayMillis: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startOfDay(dayMillis)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        return cal.timeInMillis
    }

    /** Time in the user's 12/24-hour preference, e.g. "3:30 PM" or "15:30". */
    fun formatTime(context: Context, millis: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))
}
