package com.rudy.beaware.ui.screens.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import com.rudy.beaware.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        refreshPermissions()
    }

    fun refreshPermissions() {
        _permissionState.value = PermissionState(
            hasUsageStats = PermissionHelper.hasUsageStatsPermission(context),
            hasOverlay = PermissionHelper.hasOverlayPermission(context),
            hasNotification = PermissionHelper.hasNotificationPermission(context)
        )
    }
}
