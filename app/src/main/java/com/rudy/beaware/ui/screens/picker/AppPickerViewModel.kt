package com.rudy.beaware.ui.screens.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudy.beaware.data.repository.AppRepository
import com.rudy.beaware.model.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AppPickerUiState(
    val apps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private var allApps: List<AppInfo> = emptyList()

    private val _searchQuery = MutableStateFlow("")
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<AppPickerUiState> =
        combine(_searchQuery, _selectedPackages, _isLoading) { query, selected, loading ->
            val filteredApps = allApps
                .filter { app ->
                    query.isBlank()
                            || app.label.contains(query, ignoreCase = true)
                            || app.packageName.contains(query, ignoreCase = true)
                }
                .map { app ->
                    app.copy(isSelected = app.packageName in selected)
                }
                .sortedWith(
                    compareByDescending<AppInfo> { it.isSelected }
                        .thenBy { it.label.lowercase() }
                )

            Timber.d("uiState: query='%s', selected=%d, filtered=%d", query, selected.size, filteredApps.size)

            AppPickerUiState(
                apps = filteredApps,
                searchQuery = query,
                isLoading = loading
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppPickerUiState()
        )

    init {
        Timber.d("init: loading installed apps and seeding selection")
        viewModelScope.launch(Dispatchers.IO) {
            allApps = repository.getInstalledApps()
            Timber.d("init: loaded %d installed apps", allApps.size)
            _selectedPackages.value = repository.getSelectedApps().first()
            Timber.d("init: seeded %d selected packages", _selectedPackages.value.size)
            _isLoading.value = false
        }
    }

    fun onSearchQueryChanged(query: String) {
        Timber.d("onSearchQueryChanged: '%s'", query)
        _searchQuery.value = query
    }

    fun toggleSelection(packageName: String) {
        val current = _selectedPackages.value
        val wasSelected = packageName in current
        _selectedPackages.value = if (wasSelected) {
            current - packageName
        } else {
            current + packageName
        }
        Timber.d("toggleSelection: %s %s (total selected: %d)", packageName, if (wasSelected) "DESELECTED" else "SELECTED", _selectedPackages.value.size)
    }

    suspend fun saveSelection() {
        Timber.d("saveSelection: saving %d packages", _selectedPackages.value.size)
        repository.saveSelectedApps(_selectedPackages.value)
        Timber.d("saveSelection: complete")
    }
}
