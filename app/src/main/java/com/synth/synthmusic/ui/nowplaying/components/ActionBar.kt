package com.synth.synthmusic.ui.nowplaying.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Bottom action bar with Timer, Share, Lyrics, and Visualize actions.
 *
 * @param onTimerClick Called when the timer button is pressed.
 * @param onShareClick Called when the share button is pressed.
 * @param onLyricsClick Called when the lyrics button is pressed.
 * @param onVisualizerClick Called when the visualizer button is pressed.
 * @param modifier Modifier for layout.
 */
@Composable
fun ActionBar(
    onTimerClick: () -> Unit,
    onShareClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onVisualizerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionItem(
                icon = Icons.Default.Timer,
                label = "Timer",
                onClick = onTimerClick
            )
            ActionItem(
                icon = Icons.Default.Share,
                label = "Share",
                onClick = onShareClick
            )
            ActionItem(
                icon = Icons.AutoMirrored.Filled.Article,
                label = "Lyrics",
                onClick = onLyricsClick
            )
            ActionItem(
                icon = Icons.Default.GraphicEq,
                label = "Visualize",
                onClick = onVisualizerClick
            )
        }
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
