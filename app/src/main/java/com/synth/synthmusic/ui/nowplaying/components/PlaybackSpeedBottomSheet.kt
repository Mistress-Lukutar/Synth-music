package com.synth.synthmusic.ui.nowplaying.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet for adjusting playback speed and pitch via rotary knobs.
 *
 * Drag a knob vertically to change its value.
 * Long-press a knob to reset it to 1.0x.
 *
 * @param currentSpeed Current playback speed (0.25 – 4.0).
 * @param currentPitch Current playback pitch (0.25 – 4.0).
 * @param onSpeedChanged Callback invoked when speed changes.
 * @param onPitchChanged Callback invoked when pitch changes.
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Playback Speed & Pitch",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Drag vertically to adjust · Long-press to reset",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaybackKnob(
                    label = "SPEED",
                    value = speed,
                    valueRange = 0.25f..2.0f,
                    onValueChange = {
                        speed = it
                        onSpeedChanged(it)
                    },
                    onReset = {
                        speed = 1.0f
                        onSpeedChanged(1.0f)
                    }
                )

                PlaybackKnob(
                    label = "PITCH",
                    value = pitch,
                    valueRange = 0.5f..2.0f,
                    onValueChange = {
                        pitch = it
                        onPitchChanged(it)
                    },
                    onReset = {
                        pitch = 1.0f
                        onPitchChanged(1.0f)
                    }
                )
            }
        }
    }
}
