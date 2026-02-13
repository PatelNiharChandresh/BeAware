package com.rudy.beaware.data.repository

import com.rudy.beaware.data.local.entity.UsageSessionEntity
import com.rudy.beaware.model.AppInfo
import com.rudy.beaware.model.AppUsageSummary
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    fun getInstalledApps(): List<AppInfo>
    fun getSelectedApps(): Flow<Set<String>>
    suspend fun saveSelectedApps(packages: Set<String>)
    suspend fun insertSession(session: UsageSessionEntity)
    fun getSessionsByDateRange(start: String, end: String): Flow<List<UsageSessionEntity>>
    fun getTotalDurationByApp(start: String, end: String): Flow<List<AppUsageSummary>>
}
