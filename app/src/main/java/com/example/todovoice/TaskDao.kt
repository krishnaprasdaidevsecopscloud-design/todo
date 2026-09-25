package com.example.todovoice

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    // Today + future tasks that are still TODO or IN_PROGRESS
    @Query("SELECT * FROM tasks WHERE status IN ('TODO','IN_PROGRESS') ORDER BY dueDate ASC, remindAt ASC")
    fun getActiveTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE status = 'PENDING' ORDER BY pendingUntil ASC")
    fun getPendingTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompletedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE status = 'PENDING'")
    suspend fun getPendingTasksOnce(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE calendarEventId = :eventId")
    suspend fun getByCalendarEventId(eventId: String): Task?

    @Query("SELECT * FROM tasks WHERE calendarEventId IS NOT NULL")
    suspend fun getCalendarTasksOnce(): List<Task>

    // Tasks whose due-time alarm is still in the future (rescheduled after reboot)
    @Query("SELECT * FROM tasks WHERE status IN ('TODO','PENDING') AND remindAt > :now")
    suspend fun getFutureTimed(now: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE status = 'IN_PROGRESS'")
    suspend fun getInProgressOnce(): List<Task>
}
