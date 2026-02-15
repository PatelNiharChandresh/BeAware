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

    val uiState: StateFlow<AppPickerUiState> =
        combine(_searchQuery, _selectedPackages) { query, selected ->
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

            AppPickerUiState(
                apps = filteredApps,
                searchQuery = query,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppPickerUiState()
        )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allApps = repository.getInstalledApps()
            _selectedPackages.value = repository.getSelectedApps().first()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleSelection(packageName: String) {
        val current = _selectedPackages.value
        _selectedPackages.value = if (packageName in current) {
            current - packageName
        } else {
            current + packageName
        }
    }

    fun saveSelection() {
        viewModelScope.launch {
            repository.saveSelectedApps(_selectedPackages.value)
        }
    }
}
