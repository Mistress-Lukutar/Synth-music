package com.synth.synthmusic.ui.nowplaying.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * A seek-bar styled as a waveform amplitude envelope.
 *
 * @param amplitudes Normalized amplitude list (0..1) representing the track envelope.
 * @param progress Current playback progress in the range 0..1.
 * @param onSeek Callback invoked when user drags or taps to seek.
 * @param playedColor Color for the already-played portion.
 * @param remainingColor Color for the remaining portion.
 * @param modifier Modifier for layout.
 */
@Composable
fun WaveformSlider(
    amplitudes: List<Float>,
    progress: Float,
    onSeek: (Float) -> Unit,
    playedColor: Color = Color.Cyan,
    remainingColor: Color = Color.Gray.copy(alpha = 0.4f),
    modifier: Modifier = Modifier
) {
    val safeProgress = progress.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val p = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(p)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val p = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek(p)
                    change.consume()
                }
            }
    ) {
        if (amplitudes.isEmpty()) return@Canvas

        val barCount = amplitudes.size
        val barWidth = size.width / barCount
        val gap = barWidth * 0.25f
        val drawWidth = barWidth - gap
        val maxBarHeight = size.height

        for (i in 0 until barCount) {
            val amp = amplitudes[i].coerceIn(0f, 1f)
            val barHeight = amp * maxBarHeight
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
