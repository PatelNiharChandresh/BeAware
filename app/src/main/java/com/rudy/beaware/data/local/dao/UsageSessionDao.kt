package com.rudy.beaware.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.rudy.beaware.data.local.entity.UsageSessionEntity
import com.rudy.beaware.model.AppUsageSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageSessionDao {

    @Insert
    suspend fun insertSession(session: UsageSessionEntity)

    @Query("SELECT * FROM usage_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY startTime DESC")
    fun getSessionsByDateRange(startDate: String, endDate: String): Flow<List<UsageSessionEntity>>

    @Query(
        "SELECT packageName, appLabel, SUM(durationMs) AS totalDurationMs, COUNT(*) AS sessionCount " +
        "FROM usage_sessions WHERE date BETWEEN :startDate AND :endDate " +
        "GROUP BY packageName ORDER BY totalDurationMs DESC"
    )
    fun getTotalDurationByApp(startDate: String, endDate: String): Flow<List<AppUsageSummary>>
}
