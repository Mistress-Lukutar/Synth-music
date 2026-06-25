package com.synth.synthmusic.ui.sleeptimer

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.ui.components.MenuDialog
import com.synth.synthmusic.ui.components.MenuOptionRow
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

/**
 * Sleep timer dialog with preset duration options.
 *
 * Uses the shared [MenuDialog] shell so it matches the other Now Playing menus.
 */
@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    viewModel: SleepTimerViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val options = listOf(
        10 to "10 minutes",
        20 to "20 minutes",
        30 to "30 minutes",
        45 to "45 minutes",
        60 to "1 hour"
    )

    MenuDialog(
        title = "Sleep Timer",
        onDismiss = onDismiss
    ) {
        if (uiState.isActive) {
            val statusText = if (uiState.endOfTrack) {
                "Stopping at end of track"
            } else {
                val minutes = uiState.remainingMs / 1000 / 60
                val seconds = (uiState.remainingMs / 1000) % 60
                String.format(Locale.US, "Remaining: %d:%02d", minutes, seconds)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            MenuOptionRow(
                icon = Icons.Default.Stop,
                label = "Stop Timer",
                onClick = {
                    viewModel.stopTimer()
                    onDismiss()
                }
            )
        } else {
            options.forEach { (minutes, label) ->
                MenuOptionRow(
                    icon = Icons.Default.Timer,
                    label = label,
                    onClick = {
                        viewModel.startTimer(minutes)
                        onDismiss()
                    }
                )
            }
            MenuOptionRow(
                icon = Icons.Default.MusicOff,
                label = "End of Track",
                onClick = {
                    viewModel.startEndOfTrackTimer()
                    onDismiss()
                }
            )
        }
    }
}
