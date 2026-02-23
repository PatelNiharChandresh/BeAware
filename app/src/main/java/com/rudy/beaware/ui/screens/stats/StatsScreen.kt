package com.rudy.beaware.ui.screens.stats

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudy.beaware.R
import com.rudy.beaware.model.AppUsageSummary
import com.rudy.beaware.util.TimeFormatter
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Timber.d(
        "StatsScreen: composing, range=%s, apps=%d, total=%dms",
        uiState.selectedRange, uiState.usageStats.size, uiState.totalDurationMs
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Date range selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DateRange.entries.forEachIndexed { index, range ->
                    val label = when (range) {
                        DateRange.TODAY -> stringResource(R.string.stats_range_today)
                        DateRange.THREE_DAYS -> stringResource(R.string.stats_range_three_days)
                        DateRange.WEEK -> stringResource(R.string.stats_range_week)
                        DateRange.MONTH -> stringResource(R.string.stats_range_month)
                    }
                    SegmentedButton(
                        selected = uiState.selectedRange == range,
                        onClick = { viewModel.onRangeSelected(range) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DateRange.entries.size
                        )
                    ) {
                        Text(text = label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Total screen time summary
            val formattedTotal = remember(uiState.totalDurationMs) {
                TimeFormatter.formatDuration(uiState.totalDurationMs)
            }
            Text(
                text = stringResource(R.string.stats_total_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formattedTotal,
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Content: loading / empty / data
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.usageStats.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.stats_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    val maxDurationMs = uiState.usageStats.first().totalDurationMs

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(
                            items = uiState.usageStats,
                            key = { it.packageName }
                        ) { item ->
                            UsageStatsItem(
                                item = item,
                                maxDurationMs = maxDurationMs
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageStatsItem(
    item: AppUsageSummary,
    maxDurationMs: Long
) {
    val context = LocalContext.current

    val iconBitmap = remember(item.packageName) {
        try {
            context.packageManager
                .getApplicationIcon(item.packageName)
                .toBitmap()
                .asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    val formattedDuration = remember(item.totalDurationMs) {
        TimeFormatter.formatDuration(item.totalDurationMs)
    }

    val progress = if (maxDurationMs > 0) {
        item.totalDurationMs.toFloat() / maxDurationMs.toFloat()
    } else {
        0f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = item.appLabel,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.appLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formattedDuration,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.stats_sessions, item.sessionCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
