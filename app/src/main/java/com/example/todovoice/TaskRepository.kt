package com.example.todovoice

import android.content.Context

class TaskRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).taskDao()

    fun activeTasks() = dao.getActiveTasks()
    fun pendingTasks() = dao.getPendingTasks()
    fun completedTasks() = dao.getCompletedTasks()

    suspend fun addTask(
        title: String,
        note: String,
        dueDate: Long,
        appLink: String?,
        remindAt: Long? = null,
        alarmTone: String? = null
    ): Long = insert(
        Task(
            title = title, note = note, dueDate = dueDate, appLink = appLink,
            remindAt = remindAt, alarmTone = alarmTone
        )
    )

    suspend fun insert(task: Task): Long {
        val id = dao.insert(task)
        syncDueAlarm(task.copy(id = id))
        return id
    }

    suspend fun update(task: Task) {
        dao.update(task)
        syncDueAlarm(task)
    }

    suspend fun delete(task: Task) {
        dao.delete(task)
        TaskAlarmScheduler.cancel(appContext, task.id)
        VoiceAlarmReceiver.cancel(appContext, task.id)
    }

    suspend fun markInProgress(task: Task) =
        update(task.copy(status = TaskStatus.IN_PROGRESS))

    suspend fun markPending(task: Task, newDate: Long, remindAt: Long? = null) =
        update(task.copy(status = TaskStatus.PENDING, pendingUntil = newDate, remindAt = remindAt))

    suspend fun markCompleted(task: Task) =
        update(task.copy(status = TaskStatus.COMPLETED, completedAt = System.currentTimeMillis()))

    suspend fun getPendingSnapshot(): List<Task> = dao.getPendingTasksOnce()

    suspend fun getById(id: Long): Task? = dao.getById(id)

    suspend fun getByCalendarEventId(eventId: String): Task? = dao.getByCalendarEventId(eventId)

    suspend fun getCalendarTasks(): List<Task> = dao.getCalendarTasksOnce()

    /** Alarms don't survive a reboot; re-arm everything from the DB. */
    suspend fun rescheduleAllAlarms() {
        dao.getFutureTimed(System.currentTimeMillis()).forEach { syncDueAlarm(it) }
        dao.getInProgressOnce().forEach { VoiceAlarmReceiver.schedule(appContext, it.id) }
    }

    // Due-time alarm only rings for TODO/PENDING tasks with a future time
    private fun syncDueAlarm(task: Task) {
        val at = task.remindAt
        val wantsAlarm = task.status == TaskStatus.TODO || task.status == TaskStatus.PENDING
        if (wantsAlarm && at != null && at > System.currentTimeMillis()) {
            TaskAlarmScheduler.schedule(appContext, task.id, at)
        } else {
            TaskAlarmScheduler.cancel(appContext, task.id)
        }
    }

    companion object {
        @Volatile private var INSTANCE: TaskRepository? = null
        fun getInstance(context: Context): TaskRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaskRepository(context).also { INSTANCE = it }
            }
    }
}
