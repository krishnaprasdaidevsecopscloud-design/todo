package com.example.todovoice

import androidx.core.text.HtmlCompat
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String?,
    val startMillis: Long?,     // null only for cancelled events
    val allDay: Boolean,
    val cancelled: Boolean,     // deleted, cancelled, or declined by the user
    val htmlLink: String?
)

class CalendarUnauthorizedException : IOException("Google Calendar access token rejected")

/** Minimal Google Calendar REST client (primary calendar, read-only). Call off the main thread. */
object GoogleCalendarClient {
    private const val EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events"

    fun fetchEvents(accessToken: String, fromMillis: Long, toMillis: Long): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append(EVENTS_URL)
                append("?singleEvents=true&orderBy=startTime&showDeleted=true&maxResults=250")
                append("&timeMin=").append(enc(Instant.ofEpochMilli(fromMillis).toString()))
                append("&timeMax=").append(enc(Instant.ofEpochMilli(toMillis).toString()))
                pageToken?.let { append("&pageToken=").append(enc(it)) }
            }
            val json = get(url, accessToken)
            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    parseEvent(items.getJSONObject(i))?.let(events::add)
                }
            }
            pageToken = json.optString("nextPageToken").ifEmpty { null }
        } while (pageToken != null)
        return events
    }

    private fun get(url: String, accessToken: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) throw CalendarUnauthorizedException()
            if (code !in 200..299) {
                val body = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Calendar API HTTP $code ${body.take(300)}")
            }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun parseEvent(o: JSONObject): CalendarEvent? {
        val id = o.optString("id").ifEmpty { return null }
        val declined = o.optJSONArray("attendees")?.let { attendees ->
            (0 until attendees.length()).any {
                val a = attendees.getJSONObject(it)
                a.optBoolean("self") && a.optString("responseStatus") == "declined"
            }
        } ?: false
        val start = o.optJSONObject("start")
        val dateTime = start?.optString("dateTime").orEmpty()
        val date = start?.optString("date").orEmpty()
        val startMillis = when {
            dateTime.isNotEmpty() -> OffsetDateTime.parse(dateTime).toInstant().toEpochMilli()
            date.isNotEmpty() -> LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            else -> null
        }
        val description = o.optString("description").ifEmpty { null }?.let {
            HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
        }
        return CalendarEvent(
            id = id,
            title = o.optString("summary").ifEmpty { "(No title)" },
            description = description?.ifEmpty { null },
            startMillis = startMillis,
            allDay = dateTime.isEmpty() && date.isNotEmpty(),
            cancelled = o.optString("status") == "cancelled" || declined || startMillis == null,
            htmlLink = o.optString("htmlLink").ifEmpty { null }
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
