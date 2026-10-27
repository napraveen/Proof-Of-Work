package com.proofofwork.core.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_logs")
data class VoiceLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transcript: String,
    val summary: String,
    val category: String,
    val effortMinutes: Int,
    val rewardMinutes: Int,
    val aiFeedback: String,
    val confidenceScore: Int,
    val wordCount: Int,
    val isConfession: Boolean = false,
    val dateString: String,
    val timestamp: Long = System.currentTimeMillis()
)
