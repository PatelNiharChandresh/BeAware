package com.rudy.beaware.service.overlay

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rudy.beaware.util.TimeFormatter

@Composable
fun TimerPill(
    appLabel: String,
    elapsedSeconds: Long
) {
    val formattedTime = remember(elapsedSeconds) {
        TimeFormatter.formatTimer(elapsedSeconds)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.7f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = appLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )

            Text(
                text = " \u00B7 ",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = formattedTime,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
