package com.synth.synthmusic.ui.sleeptimer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

/**
 * Sleep timer dialog with preset duration options.
 */
@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    viewModel: SleepTimerViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selected by remember { mutableStateOf<Int?>(null) }

    val options = listOf(
        10 to "10 minutes",
        20 to "20 minutes",
        30 to "30 minutes",
        45 to "45 minutes",
        60 to "1 hour"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column {
                if (uiState.isActive) {
                    val minutes = uiState.remainingMs / 1000 / 60
                    val seconds = (uiState.remainingMs / 1000) % 60
                    Text(
                        text = if (uiState.endOfTrack) "Stopping at end of track"
                        else String.format("Remaining: %d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextButton(
                        onClick = { viewModel.stopTimer() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stop Timer")
                    }
                } else {
                    options.forEach { (minutes, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selected == minutes,
                                onClick = { selected = minutes }
                            )
                            Text(label)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selected == -1,
                            onClick = { selected = -1 }
                        )
                        Text("End of Track")
                    }
                }
            }
        },
        confirmButton = {
            if (!uiState.isActive) {
                TextButton(
                    onClick = {
                        when (selected) {
                            -1 -> viewModel.startEndOfTrackTimer()
                            null -> {}
                            else -> viewModel.startTimer(selected!!)
                        }
                        onDismiss()
                    },
                    enabled = selected != null
                ) {
                    Text("Start")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
