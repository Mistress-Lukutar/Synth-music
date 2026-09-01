package com.synth.synthmusic.ui.nowplaying.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * A seek-bar styled as a waveform amplitude envelope.
 *
 * While the user drags, the highlighted portion follows the finger via local state
 * and the seek is committed on drag end; taps seek immediately. Gesture detectors
 * are keyed on [Unit] and read the latest callback through [rememberUpdatedState],
 * because the caller recomposes (and recreates [onSeek]) on every position poll —
 * keying `pointerInput` on the lambda would cancel in-flight gestures.
 *
 * @param amplitudes Normalized amplitude list (0..1) representing the track envelope.
 * @param progress Current playback progress in the range 0..1.
 * @param onSeek Callback invoked with the target fraction when the user seeks.
 * @param playedColor Color for the already-played portion.
 * @param remainingColor Color for the remaining portion.
 * @param modifier Modifier for layout.
 */
@Composable
fun WaveformSlider(
    amplitudes: List<Float>,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    playedColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    remainingColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    var dragProgress by remember { mutableStateOf<Float?>(null) }

    val safeProgress = (dragProgress ?: progress).coerceIn(0f, 1f)

    fun fractionAt(x: Float, width: Int): Float =
        if (width > 0) (x / width).coerceIn(0f, 1f) else 0f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentOnSeek(fractionAt(offset.x, size.width))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragProgress = fractionAt(offset.x, size.width)
                    },
                    onDrag = { change, _ ->
                        dragProgress = fractionAt(change.position.x, size.width)
                        change.consume()
                    },
                    onDragEnd = {
                        dragProgress?.let { currentOnSeek(it) }
                        dragProgress = null
                    },
                    onDragCancel = {
                        dragProgress = null
                    }
                )
            }
    ) {
        if (amplitudes.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas

        val barCount = amplitudes.size
        val barWidth = size.width / barCount
        val gap = barWidth * 0.45f
        val drawWidth = (barWidth - gap).coerceAtLeast(1.5f)
        val maxBarHeight = size.height

        for (i in 0 until barCount) {
            val amp = amplitudes[i].coerceIn(0f, 1f)
            val barHeight = (0.1f + amp * 0.9f) * maxBarHeight
            val x = i * barWidth + gap / 2
            val y = (size.height - barHeight) / 2
            val barProgress = i.toFloat() / barCount

            val color = if (barProgress <= safeProgress) playedColor else remainingColor
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(drawWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(drawWidth / 2, drawWidth / 2)
            )
        }
    }
}
