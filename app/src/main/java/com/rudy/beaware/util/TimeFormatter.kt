package com.rudy.beaware.util

object TimeFormatter {

    fun formatDuration(ms: Long): String {
        if (ms < 0) return "0s"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    fun formatTimer(seconds: Long): String {
        if (seconds < 0) return "00:00"
        val minutes = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(minutes, secs)
    }
}
