package com.rudy.beaware.ui.screens.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import com.rudy.beaware.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

data class PermissionState(
    val hasUsageStats: Boolean = false,
    val hasOverlay: Boolean = false,
    val hasNotification: Boolean = false
) {
    val allGranted: Boolean
        get() = hasUsageStats && hasOverlay && hasNotification
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    init {
        Timber.d("init: checking initial permissions")
        refreshPermissions()
    }

    fun refreshPermissions() {
        val usageStats = PermissionHelper.hasUsageStatsPermission(context)
        val overlay = PermissionHelper.hasOverlayPermission(context)
        val notification = PermissionHelper.hasNotificationPermission(context)

        Timber.d("refreshPermissions: usageStats=%s, overlay=%s, notification=%s", usageStats, overlay, notification)

        val newState = PermissionState(
            hasUsageStats = usageStats,
            hasOverlay = overlay,
            hasNotification = notification
        )
        _permissionState.value = newState

        Timber.d("refreshPermissions: allGranted=%s", newState.allGranted)
    }
}
