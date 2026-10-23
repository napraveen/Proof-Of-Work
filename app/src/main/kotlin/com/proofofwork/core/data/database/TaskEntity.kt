package com.proofofwork.core.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val rewardMinutes: Int = 15,
    val isCompletedToday: Boolean = false,
    val completedAtTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
