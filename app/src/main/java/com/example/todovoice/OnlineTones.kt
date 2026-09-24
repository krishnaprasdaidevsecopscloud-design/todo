package com.example.todovoice

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class OnlineTone(val name: String, val url: String)

/** Free alarm sounds from the Google Sound Library. Add entries here to grow the list. */
object OnlineToneCatalog {
    private const val BASE = "https://actions.google.com/sounds/v1/alarms/"

    val tones = listOf(
        OnlineTone("Alarm clock", BASE + "alarm_clock.ogg"),
        OnlineTone("Digital watch alarm", BASE + "digital_watch_alarm_long.ogg"),
        OnlineTone("Mechanical clock ring", BASE + "mechanical_clock_ring.ogg"),
        OnlineTone("Winding alarm clock", BASE + "winding_alarm_clock.ogg"),
        OnlineTone("Bugle tune", BASE + "bugle_tune.ogg"),
        OnlineTone("Spaceship alarm", BASE + "spaceship_alarm.ogg"),
        OnlineTone("Bell ringing", BASE + "medium_bell_ringing_near.ogg"),
        OnlineTone("Dinner bell (triangle)", BASE + "dinner_bell_triangle.ogg"),
        OnlineTone("Short beep", BASE + "beep_short.ogg")
    )
}

/** Downloads an online tone into app storage so the alarm still rings offline. */
object ToneDownloader {

    suspend fun download(context: Context, tone: OnlineTone): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "tones").apply { mkdirs() }
        val target = File(dir, tone.url.substringAfterLast('/'))
        if (target.exists() && target.length() > 0) return@withContext Uri.fromFile(target)

        val partial = File(dir, target.name + ".part")
        val conn = (URL(tone.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("server returned HTTP ${conn.responseCode}")
            conn.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
        } catch (e: Exception) {
            partial.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        if (!partial.renameTo(target)) throw IOException("couldn't save tone")
        Uri.fromFile(target)
    }
}
