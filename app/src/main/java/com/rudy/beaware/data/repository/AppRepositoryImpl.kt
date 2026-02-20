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
import timber.log.Timber
import javax.inject.Inject

class AppRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsDataStore: PrefsDataStore,
    private val usageSessionDao: UsageSessionDao
) : AppRepository {

    override fun getInstalledApps(): List<AppInfo> {
        Timber.d("getInstalledApps: querying PackageManager")
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolvedApps = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        Timber.d("getInstalledApps: PackageManager returned %d activities", resolvedApps.size)

        val apps = resolvedApps
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

        Timber.d("getInstalledApps: returning %d apps (filtered, deduplicated, sorted)", apps.size)
        return apps
    }

    override fun getSelectedApps(): Flow<Set<String>> {
        Timber.d("getSelectedApps: delegating to PrefsDataStore")
        return prefsDataStore.getSelectedApps()
    }

    override suspend fun saveSelectedApps(packages: Set<String>) {
        Timber.d("saveSelectedApps: saving %d packages", packages.size)
        prefsDataStore.setSelectedApps(packages)
    }

    override suspend fun insertSession(session: UsageSessionEntity) {
        Timber.d("insertSession: pkg=%s, duration=%dms, date=%s", session.packageName, session.durationMs, session.date)
        usageSessionDao.insertSession(session)
        Timber.d("insertSession: complete")
    }

    override fun getSessionsByDateRange(
        start: String,
        end: String
    ): Flow<List<UsageSessionEntity>> {
        Timber.d("getSessionsByDateRange: %s to %s", start, end)
        return usageSessionDao.getSessionsByDateRange(start, end)
    }

    override fun getTotalDurationByApp(
        start: String,
        end: String
    ): Flow<List<AppUsageSummary>> {
        Timber.d("getTotalDurationByApp: %s to %s", start, end)
        return usageSessionDao.getTotalDurationByApp(start, end)
    }

    override fun isTrackingActive(): Flow<Boolean> {
        Timber.d("isTrackingActive: delegating to PrefsDataStore")
        return prefsDataStore.isTrackingActive()
    }

    override suspend fun setTrackingActive(active: Boolean) {
        Timber.d("setTrackingActive: %s", active)
        prefsDataStore.setTrackingActive(active)
    }
}
