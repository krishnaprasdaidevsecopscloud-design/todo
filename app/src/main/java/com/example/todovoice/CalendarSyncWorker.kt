package com.example.todovoice

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Pulls the next SYNC_DAYS of Google Calendar events and mirrors them as tasks
 * with an alarm LEAD minutes before each event. Only tasks still in TODO are
 * updated or removed: once the user acts on a task, the calendar never overrides it.
 */
class CalendarSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!AppPrefs.isCalendarConnected(ctx)) return Result.success()

        val now = System.currentTimeMillis()
        val windowStart = DateUtils.startOfDay(now)
        val windowEnd = windowStart + (SYNC_DAYS + 1) * DAY_MS // day boundary, see removeDeleted()

        val events = try {
            fetchWithRetryOnUnauthorized(windowStart, windowEnd) ?: run {
                // pause syncing until the user reconnects, so this notification appears once, not hourly
                AppPrefs.setCalendarConnected(ctx, false, AppPrefs.getCalendarAccount(ctx))
                NotificationHelper.showCalendarReconnect(ctx)
                return Result.failure(workDataOf(KEY_ERROR to "Google Calendar access expired. Tap Connect again."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = GoogleCalendarAuth.describeError(e)
            return if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to message))
        }

        applyEvents(events, now, windowStart, windowEnd)
        AppPrefs.setLastSync(ctx, now)
        return Result.success()
    }

    /** Null when the user must reconnect (consent revoked or token can't be refreshed). */
    private suspend fun fetchWithRetryOnUnauthorized(from: Long, to: Long): List<CalendarEvent>? {
        val ctx = applicationContext
        val token = GoogleCalendarAuth.getTokenSilently(ctx) ?: return null
        return try {
            GoogleCalendarClient.fetchEvents(token, from, to)
        } catch (e: CalendarUnauthorizedException) {
            GoogleCalendarAuth.clearToken(ctx, token)
            val fresh = GoogleCalendarAuth.getTokenSilently(ctx) ?: return null
            try {
                GoogleCalendarClient.fetchEvents(fresh, from, to)
            } catch (e2: CalendarUnauthorizedException) {
                null
            }
        }
    }

    private suspend fun applyEvents(events: List<CalendarEvent>, now: Long, windowStart: Long, windowEnd: Long) {
        val ctx = applicationContext
        val repo = TaskRepository.getInstance(ctx)
        val leadMs = AppPrefs.getLeadMinutes(ctx) * 60_000L
        val live = events.filter { !it.cancelled && it.startMillis != null }

        for (event in live) {
            val start = event.startMillis!!
            val remindAt = if (event.allDay) null else start - leadMs
            val existing = repo.getByCalendarEventId(event.id)
            val fields = (existing ?: Task(title = "", note = "", dueDate = 0)).copy(
                title = event.title,
                note = event.description?.take(MAX_NOTE_CHARS) ?: "From Google Calendar",
                dueDate = DateUtils.startOfDay(start),
                remindAt = remindAt,
                appLink = event.htmlLink,
                calendarEventId = event.id
            )

            val saved = when {
                existing == null -> fields.copy(id = repo.insert(fields))
                existing.status == TaskStatus.TODO && fields != existing -> fields.also { repo.update(it) }
                else -> null
            }

            // Synced within the lead window (e.g. event created 5 min before it starts):
            // remindAt is already past, so ring at the event start instead. Runs only on
            // insert/change, so the next sync doesn't ring again.
            if (saved != null && remindAt != null && remindAt <= now && start > now) {
                TaskAlarmScheduler.schedule(ctx, saved.id, start)
            }
        }

        removeDeleted(repo, live.map { it.id }.toSet(), windowStart, windowEnd)
    }

    /**
     * Events come back when they end after windowStart and start before windowEnd, so any TODO
     * calendar task dated inside that window but missing from [liveIds] was deleted, cancelled or
     * declined. windowEnd sits on a day boundary, so dueDate < windowEnd <=> start < windowEnd.
     */
    private suspend fun removeDeleted(repo: TaskRepository, liveIds: Set<String>, windowStart: Long, windowEnd: Long) {
        repo.getCalendarTasks()
            .filter { it.status == TaskStatus.TODO && it.calendarEventId !in liveIds }
            .filter { it.dueDate in windowStart until windowEnd }
            .forEach { repo.delete(it) }
    }

    companion object {
        const val ONE_TIME_WORK = "calendar_sync_now"
        const val KEY_ERROR = "error"
        private const val PERIODIC_WORK = "calendar_sync_periodic"
        private const val SYNC_DAYS = 7L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val MAX_RETRIES = 3
        private const val MAX_NOTE_CHARS = 300

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(PERIODIC_WORK)
                cancelUniqueWork(ONE_TIME_WORK)
            }
        }
    }
}
