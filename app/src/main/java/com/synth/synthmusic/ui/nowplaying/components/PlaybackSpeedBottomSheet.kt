package com.synth.synthmusic.ui.nowplaying.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Bottom sheet for adjusting playback speed and pitch.
 *
 * @param currentSpeed Current playback speed (0.25 – 4.0).
 * @param currentPitch Current playback pitch (0.25 – 4.0).
 * @param onSpeedChanged Callback invoked when speed slider is released.
 * @param onPitchChanged Callback invoked when pitch slider is released.
 * @param onDismiss Callback invoked when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedBottomSheet(
    currentSpeed: Float,
    currentPitch: Float,
    onSpeedChanged: (Float) -> Unit,
    onPitchChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var speed by remember { mutableFloatStateOf(currentSpeed) }
    var pitch by remember { mutableFloatStateOf(currentPitch) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Playback Speed & Pitch",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Speed section
            Text(
                text = "Speed: ${String.format("%.2f", speed)}x",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = speed,
                onValueChange = { speed = it },
                onValueChangeFinished = { onSpeedChanged(speed) },
                valueRange = 0.25f..2.0f,
                steps = 6, // 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SpeedPresetButton(label = "0.5x", target = 0.5f, onClick = { speed = it; onSpeedChanged(it) })
                SpeedPresetButton(label = "0.75x", target = 0.75f, onClick = { speed = it; onSpeedChanged(it) })
                SpeedPresetButton(label = "1.0x", target = 1.0f, onClick = { speed = it; onSpeedChanged(it) })
                SpeedPresetButton(label = "1.25x", target = 1.25f, onClick = { speed = it; onSpeedChanged(it) })
                SpeedPresetButton(label = "1.5x", target = 1.5f, onClick = { speed = it; onSpeedChanged(it) })
                SpeedPresetButton(label = "2.0x", target = 2.0f, onClick = { speed = it; onSpeedChanged(it) })
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Pitch section
            Text(
                text = "Pitch: ${String.format("%.2f", pitch)}x",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = pitch,
                onValueChange = { pitch = it },
                onValueChangeFinished = { onPitchChanged(pitch) },
                valueRange = 0.5f..2.0f,
                steps = 5, // 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SpeedPresetButton(label = "0.5x", target = 0.5f, onClick = { pitch = it; onPitchChanged(it) })
                SpeedPresetButton(label = "0.75x", target = 0.75f, onClick = { pitch = it; onPitchChanged(it) })
                SpeedPresetButton(label = "1.0x", target = 1.0f, onClick = { pitch = it; onPitchChanged(it) })
                SpeedPresetButton(label = "1.25x", target = 1.25f, onClick = { pitch = it; onPitchChanged(it) })
                SpeedPresetButton(label = "1.5x", target = 1.5f, onClick = { pitch = it; onPitchChanged(it) })
                SpeedPresetButton(label = "2.0x", target = 2.0f, onClick = { pitch = it; onPitchChanged(it) })
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = {
                        speed = 1.0f
                        pitch = 1.0f
                        onSpeedChanged(1.0f)
                        onPitchChanged(1.0f)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }
                ) {
                    Text("Reset to Default")
                }
            }
        }
    }
}

@Composable
private fun SpeedPresetButton(
    label: String,
    target: Float,
    onClick: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = { onClick(target) },
        modifier = modifier.width(52.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
