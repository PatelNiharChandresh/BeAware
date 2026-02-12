package com.rudy.beaware.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_sessions")
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val date: String // "yyyy-MM-dd"
)
