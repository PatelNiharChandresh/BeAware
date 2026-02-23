package com.rudy.beaware.service.overlay

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rudy.beaware.util.TimeFormatter

@Composable
fun TimerPill(
    elapsedSeconds: Long
) {
    val formattedTime = remember(elapsedSeconds) {
        TimeFormatter.formatTimer(elapsedSeconds)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFFE91E63),
        shadowElevation = 4.dp
    ) {
        Text(
            text = formattedTime,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
