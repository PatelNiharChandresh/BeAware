package com.rudy.beaware.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.rudy.beaware.data.datastore.PrefsDataStore
import com.rudy.beaware.data.local.dao.UsageSessionDao
import com.rudy.beaware.data.local.entity.UsageSessionEntity
import com.rudy.beaware.model.AppInfo
import com.rudy.beaware.model.AppUsageSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AppRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsDataStore: PrefsDataStore,
    private val usageSessionDao: UsageSessionDao
) : AppRepository {

    override fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolvedApps = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

        return resolvedApps
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { resolveInfo ->
                AppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(pm).toString(),
                    icon = resolveInfo.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    override fun getSelectedApps(): Flow<Set<String>> {
        return prefsDataStore.getSelectedApps()
    }

    override suspend fun saveSelectedApps(packages: Set<String>) {
        prefsDataStore.setSelectedApps(packages)
    }

    override suspend fun insertSession(session: UsageSessionEntity) {
        usageSessionDao.insertSession(session)
    }

    override fun getSessionsByDateRange(
        start: String,
        end: String
    ): Flow<List<UsageSessionEntity>> {
        return usageSessionDao.getSessionsByDateRange(start, end)
    }

    override fun getTotalDurationByApp(
        start: String,
        end: String
    ): Flow<List<AppUsageSummary>> {
        return usageSessionDao.getTotalDurationByApp(start, end)
    }

    override fun isTrackingActive(): Flow<Boolean> {
        return prefsDataStore.isTrackingActive()
    }

    override suspend fun setTrackingActive(active: Boolean) {
        prefsDataStore.setTrackingActive(active)
    }
}
