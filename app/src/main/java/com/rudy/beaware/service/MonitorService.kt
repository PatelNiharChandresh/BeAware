package com.rudy.beaware.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rudy.beaware.R
import com.rudy.beaware.data.local.dao.UsageSessionDao
import com.rudy.beaware.data.local.entity.UsageSessionEntity
import com.rudy.beaware.data.repository.AppRepository
import com.rudy.beaware.service.overlay.FloatingTimerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "monitor_channel"
        const val ACTION_STOP = "com.rudy.beaware.STOP_TRACKING"
    }

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var usageSessionDao: UsageSessionDao

    private lateinit var usageStatsManager: UsageStatsManager

    private var timerManager: FloatingTimerManager? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var trackedPackages: Set<String> = emptySet()

    private var activePackage: String? = null
    private var activeLabel: String? = null
    private var sessionStartTime: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                if (activePackage != null) {
                    saveActiveSession(System.currentTimeMillis())
                }
                repository.setTrackingActive(false)
                withContext(Dispatchers.Main) {
                    timerManager?.hide()
                    timerManager = null
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }

        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val stopIntent = Intent(this, MonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_tracking_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(R.drawable.ic_notification, getString(R.string.notification_stop), stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        timerManager = FloatingTimerManager(this)

        serviceScope.launch {
            trackedPackages = repository.getSelectedApps().first()

            while (isActive) {
                checkForeground()
                delay(3_000)
            }
        }

        return START_STICKY
    }

    private suspend fun checkForeground() {
        val now = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 10_000,
            now
        )

        val currentPackage = stats
            .filter { it.lastTimeUsed > 0 }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName

        when {
            currentPackage != null
                    && currentPackage in trackedPackages
                    && currentPackage != activePackage -> {

                if (activePackage != null) {
                    saveActiveSession(now)
                }

                activePackage = currentPackage
                activeLabel = resolveAppLabel(currentPackage)
                sessionStartTime = now

                withContext(Dispatchers.Main) {
                    timerManager?.show(activeLabel!!)
                }
            }

            currentPackage != null
                    && currentPackage in trackedPackages
                    && currentPackage == activePackage -> {

                val elapsedSeconds = (now - sessionStartTime) / 1000
                withContext(Dispatchers.Main) {
                    timerManager?.updateTimer(elapsedSeconds)
                }
            }

            else -> {
                if (activePackage != null) {
                    saveActiveSession(now)
                }

                withContext(Dispatchers.Main) {
                    timerManager?.hide()
                }

                activePackage = null
                activeLabel = null
                sessionStartTime = 0L
            }
        }
    }

    private suspend fun saveActiveSession(endTime: Long) {
        val durationMs = endTime - sessionStartTime
        if (durationMs < 1_000) return

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = dateFormat.format(Date(sessionStartTime))

        val session = UsageSessionEntity(
            packageName = activePackage!!,
            appLabel = activeLabel ?: activePackage!!,
            startTime = sessionStartTime,
            endTime = endTime,
            durationMs = durationMs,
            date = date
        )

        usageSessionDao.insertSession(session)
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        activePackage = null
        activeLabel = null
        sessionStartTime = 0L
    }
}
