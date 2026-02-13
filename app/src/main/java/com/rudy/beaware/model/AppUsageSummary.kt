package com.rudy.beaware.model

data class AppUsageSummary(
    val packageName: String,
    val appLabel: String,
    val totalDurationMs: Long,
    val sessionCount: Int
)
