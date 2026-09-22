package com.example.todovoice

import android.content.Context

class TaskRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).taskDao()

    fun activeTasks() = dao.getActiveTasks()
    fun pendingTasks() = dao.getPendingTasks()
    fun completedTasks() = dao.getCompletedTasks()

    suspend fun addTask(title: String, note: String, dueDate: Long, appLink: String?): Long =
        dao.insert(Task(title = title, note = note, dueDate = dueDate, appLink = appLink))

    suspend fun markInProgress(task: Task) =
        dao.update(task.copy(status = TaskStatus.IN_PROGRESS))

    suspend fun markPending(task: Task, newDate: Long) =
        dao.update(task.copy(status = TaskStatus.PENDING, pendingUntil = newDate))

    suspend fun markCompleted(task: Task) =
        dao.update(task.copy(status = TaskStatus.COMPLETED, completedAt = System.currentTimeMillis()))

    suspend fun getPendingSnapshot(): List<Task> = dao.getPendingTasksOnce()

    suspend fun getById(id: Long): Task? = dao.getById(id)

    companion object {
        @Volatile private var INSTANCE: TaskRepository? = null
        fun getInstance(context: Context): TaskRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaskRepository(context).also { INSTANCE = it }
            }
    }
}
