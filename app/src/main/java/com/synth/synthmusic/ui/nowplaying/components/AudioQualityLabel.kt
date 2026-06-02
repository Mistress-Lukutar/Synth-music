package com.synth.synthmusic.ui.nowplaying.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

/**
 * Displays a short audio quality label such as "44.1 kHz • 320 kbps • MP3".
 *
 * @param label The formatted quality string.
 * @param modifier Modifier for layout.
 */
@Composable
fun AudioQualityLabel(
    label: String,
    modifier: Modifier = Modifier
) {
    if (label.isBlank()) return

    Text(
        text = label,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        textAlign = TextAlign.Center
    )
}
