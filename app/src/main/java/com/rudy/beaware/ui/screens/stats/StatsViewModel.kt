package com.rudy.beaware.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudy.beaware.data.repository.AppRepository
import com.rudy.beaware.model.AppUsageSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class DateRange(val daysBack: Int) {
    TODAY(0),
    THREE_DAYS(2),
    WEEK(6),
    MONTH(29)
}

data class StatsUiState(
    val usageStats: List<AppUsageSummary> = emptyList(),
    val selectedRange: DateRange = DateRange.TODAY,
    val totalDurationMs: Long = 0L,
    val isLoading: Boolean = true
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(DateRange.TODAY)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatsUiState> =
        _selectedRange
            .flatMapLatest { range ->
                Timber.d("uiState: range=%s", range)
                val (startDate, endDate) = calculateDateBounds(range)
                Timber.d("uiState: querying %s to %s", startDate, endDate)
                repository.getTotalDurationByApp(startDate, endDate)
            }
            .map { stats ->
                val totalMs = stats.sumOf { it.totalDurationMs }
                Timber.d("uiState: %d apps, totalDuration=%dms", stats.size, totalMs)
                StatsUiState(
                    usageStats = stats,
                    selectedRange = _selectedRange.value,
                    totalDurationMs = totalMs,
                    isLoading = false
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StatsUiState()
            )

    fun onRangeSelected(range: DateRange) {
        Timber.d("onRangeSelected: %s", range)
        _selectedRange.value = range
    }

    private fun calculateDateBounds(range: DateRange): Pair<String, String> {
        val today = LocalDate.now()
        val startDate = today.minusDays(range.daysBack.toLong())
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        return Pair(startDate.format(formatter), today.format(formatter))
    }
}
