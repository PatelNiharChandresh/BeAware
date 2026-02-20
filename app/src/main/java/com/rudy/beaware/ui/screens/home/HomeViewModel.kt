package com.rudy.beaware.ui.screens.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudy.beaware.R
import com.rudy.beaware.data.repository.AppRepository
import com.rudy.beaware.model.AppInfo
import com.rudy.beaware.service.MonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
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

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _toastEvent = Channel<String>(Channel.BUFFERED)
    val toastEvent = _toastEvent.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> =
        combine(
            repository.getSelectedApps(),
            repository.isTrackingActive(),
            _allApps
        ) { selectedPackages, isTracking, allApps ->
            val trackedApps = allApps.filter { it.packageName in selectedPackages }

            Timber.d("uiState: selectedPackages=%d, trackedApps=%d, isTracking=%s, allAppsLoaded=%d",
                selectedPackages.size, trackedApps.size, isTracking, allApps.size)

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
        Timber.d("init: loading installed apps")
        viewModelScope.launch(Dispatchers.IO) {
            _allApps.value = repository.getInstalledApps()
            Timber.d("init: loaded %d installed apps", _allApps.value.size)
        }
    }

    fun toggleTracking() {
        viewModelScope.launch {
            val currentState = uiState.value
            Timber.d("toggleTracking: current isTracking=%s, trackedApps=%d", currentState.isTracking, currentState.trackedApps.size)

            if (currentState.isTracking) {
                // Stop tracking — send ACTION_STOP for graceful shutdown
                Timber.d("toggleTracking: sending ACTION_STOP to MonitorService")
                val intent = Intent(context, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_STOP
                }
                context.startService(intent)
            } else {
                // Guard: no apps selected
                if (currentState.trackedApps.isEmpty()) {
                    Timber.w("toggleTracking: no apps selected, showing toast")
                    _toastEvent.send(context.getString(R.string.home_no_apps_toast))
                    return@launch
                }

                // Start tracking
                Timber.d("toggleTracking: starting MonitorService with %d tracked apps", currentState.trackedApps.size)
                repository.setTrackingActive(true)
                val intent = Intent(context, MonitorService::class.java)
                context.startForegroundService(intent)
                Timber.d("toggleTracking: startForegroundService called")
            }
        }
    }
}
