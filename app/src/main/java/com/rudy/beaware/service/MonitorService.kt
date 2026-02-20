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
import timber.log.Timber
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
        Timber.d("onStartCommand: action=%s, flags=%d, startId=%d", intent?.action, flags, startId)

        if (intent?.action == ACTION_STOP) {
            Timber.d("onStartCommand: ACTION_STOP received, starting graceful shutdown")
            serviceScope.launch {
                if (activePackage != null) {
                    Timber.d("onStartCommand: saving active session for %s before stopping", activePackage)
                    saveActiveSession(System.currentTimeMillis())
                }
                Timber.d("onStartCommand: setting tracking inactive")
                repository.setTrackingActive(false)
                withContext(Dispatchers.Main) {
                    Timber.d("onStartCommand: hiding overlay and calling stopSelf")
                    timerManager?.hide()
                    timerManager = null
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }

        Timber.d("onStartCommand: normal start — initializing service")
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        Timber.d("onStartCommand: notification channel created")

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
            Timber.d("onStartCommand: startForeground with FOREGROUND_SERVICE_TYPE_SPECIAL_USE (API 34+)")
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            Timber.d("onStartCommand: startForeground (pre-API 34)")
            startForeground(NOTIFICATION_ID, notification)
        }

        timerManager = FloatingTimerManager(this)
        Timber.d("onStartCommand: FloatingTimerManager created")

        serviceScope.launch {
            trackedPackages = repository.getSelectedApps().first()
            Timber.d("onStartCommand: loaded %d tracked packages: %s", trackedPackages.size, trackedPackages)

            Timber.d("onStartCommand: starting polling loop (3s interval)")
            while (isActive) {
                checkForeground()
                delay(3_000)
            }
            Timber.d("onStartCommand: polling loop ended")
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

        Timber.d("checkForeground: currentPackage=%s, activePackage=%s, isTracked=%s",
            currentPackage, activePackage, currentPackage?.let { it in trackedPackages })

        when {
            currentPackage != null
                    && currentPackage in trackedPackages
                    && currentPackage != activePackage -> {

                Timber.d("checkForeground: CASE A — new tracked app detected: %s", currentPackage)
                if (activePackage != null) {
                    Timber.d("checkForeground: saving previous session for %s", activePackage)
                    saveActiveSession(now)
                }

                activePackage = currentPackage
                activeLabel = resolveAppLabel(currentPackage)
                sessionStartTime = now
                Timber.d("checkForeground: new session started — label=%s", activeLabel)

                withContext(Dispatchers.Main) {
                    timerManager?.show(activeLabel!!)
                }
            }

            currentPackage != null
                    && currentPackage in trackedPackages
                    && currentPackage == activePackage -> {

                val elapsedSeconds = (now - sessionStartTime) / 1000
                Timber.d("checkForeground: CASE B — same app %s, elapsed=%ds", activePackage, elapsedSeconds)
                withContext(Dispatchers.Main) {
                    timerManager?.updateTimer(elapsedSeconds)
                }
            }

            else -> {
                if (activePackage != null) {
                    Timber.d("checkForeground: CASE C — left tracked app %s, saving session", activePackage)
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
        if (durationMs < 1_000) {
            Timber.d("saveActiveSession: skipping — duration %dms < 1000ms", durationMs)
            return
        }

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

        Timber.d("saveActiveSession: pkg=%s, duration=%dms, date=%s", session.packageName, durationMs, date)
        usageSessionDao.insertSession(session)
        Timber.d("saveActiveSession: insert complete")
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(appInfo).toString()
            Timber.d("resolveAppLabel: %s → %s", packageName, label)
            label
        } catch (_: PackageManager.NameNotFoundException) {
            Timber.w("resolveAppLabel: %s not found, using package name", packageName)
            packageName
        }
    }

    override fun onDestroy() {
        Timber.d("onDestroy: cleaning up")
        super.onDestroy()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        activePackage = null
        activeLabel = null
        sessionStartTime = 0L
        Timber.d("onDestroy: complete")
    }
}
