package com.proofofwork.core.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompletedToday ASC, id DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompletedToday = 1")
    suspend fun getCompletedTasksToday(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("UPDATE tasks SET isCompletedToday = 0, completedAtTimestamp = 0")
    suspend fun resetDailyTasks()

    // Voice Proof-of-Work Logs
    @Query("SELECT * FROM voice_logs ORDER BY timestamp DESC")
    fun getAllVoiceLogsFlow(): Flow<List<VoiceLogEntity>>

    @Query("SELECT * FROM voice_logs WHERE dateString = :dateStr ORDER BY timestamp DESC")
    suspend fun getVoiceLogsForDate(dateStr: String): List<VoiceLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceLog(log: VoiceLogEntity): Long

    @Delete
    suspend fun deleteVoiceLog(log: VoiceLogEntity)
}
