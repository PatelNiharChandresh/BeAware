package com.rudy.beaware.ui.screens.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudy.beaware.data.repository.AppRepository
import com.rudy.beaware.model.AppInfo
import com.rudy.beaware.service.MonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val trackedApps: List<AppInfo> = emptyList(),
    val isTracking: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AppRepository
) : ViewModel() {

    private var allApps: List<AppInfo> = emptyList()

    private val _toastEvent = Channel<String>(Channel.BUFFERED)
    val toastEvent = _toastEvent.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> =
        combine(
            repository.getSelectedApps(),
            repository.isTrackingActive()
        ) { selectedPackages, isTracking ->
            val trackedApps = allApps.filter { it.packageName in selectedPackages }

            HomeUiState(
                trackedApps = trackedApps,
                isTracking = isTracking,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allApps = repository.getInstalledApps()
        }
    }

    fun toggleTracking() {
        viewModelScope.launch {
            val currentState = uiState.value

            if (currentState.isTracking) {
                // Stop tracking
                val intent = Intent(context, MonitorService::class.java)
                context.stopService(intent)
                repository.setTrackingActive(false)
            } else {
                // Guard: no apps selected
                if (currentState.trackedApps.isEmpty()) {
                    _toastEvent.send("Select apps to track first")
                    return@launch
                }

                // Start tracking
                repository.setTrackingActive(true)
                val intent = Intent(context, MonitorService::class.java)
                context.startForegroundService(intent)
            }
        }
    }
}
