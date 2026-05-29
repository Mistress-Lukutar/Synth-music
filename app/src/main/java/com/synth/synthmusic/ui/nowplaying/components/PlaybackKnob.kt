package com.synth.synthmusic.ui.nowplaying.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A rotary knob control for adjusting a continuous value.
 *
 * Tap anywhere on the knob arc to jump to that value.
 * Drag along the arc to scrub the value smoothly.
 * Long-press to reset to the default value.
 *
 * @param label Label displayed above the value (e.g. "SPEED").
 * @param value Current value.
 * @param valueRange Allowed range for the value.
 * @param onValueChange Callback invoked when the value changes via tap or drag.
 * @param onReset Callback invoked on long-press to reset.
 * @param modifier Modifier for layout.
 */
@Composable
fun PlaybackKnob(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val indicatorColor = MaterialTheme.colorScheme.onSurface
    val textColor = MaterialTheme.colorScheme.onSurface

    val minAngle = 135f
    val maxAngle = 405f
    val sweep = maxAngle - minAngle

    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)
    val angle = minAngle + sweep * fraction

    /**
     * Converts a canvas [offset] (relative to the top-left) into a value
     * by computing the polar angle from the centre.
     */
    fun offsetToValue(offset: Offset, canvasSize: Float): Float {
        val center = canvasSize / 2f
        val dx = offset.x - center
        val dy = offset.y - center
        var touchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        // Normalise to [0, 360)
        if (touchAngle < 0) touchAngle += 360f

        // The effective knob range is [135, 405].
        // Angles below 135 belong to the upper wrap-around (add 360).
        val effectiveAngle = if (touchAngle < minAngle) touchAngle + 360f else touchAngle
        val clamped = effectiveAngle.coerceIn(minAngle, maxAngle)

        val touchFraction = ((clamped - minAngle) / sweep).coerceIn(0f, 1f)
        return valueRange.start + touchFraction * (valueRange.endInclusive - valueRange.start)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newValue = offsetToValue(change.position, size.width.toFloat())
                        onValueChange(newValue)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onReset() },
                        onTap = { offset ->
                            val newValue = offsetToValue(offset, size.width.toFloat())
                            onValueChange(newValue)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val strokeWidth = 12.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Background track arc
                drawArc(
                    color = trackColor,
                    startAngle = minAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - radius,
                        center.y - radius
                    ),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress arc
                drawArc(
                    color = progressColor,
                    startAngle = minAngle,
                    sweepAngle = sweep * fraction,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - radius,
                        center.y - radius
                    ),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Indicator dot — positioned on the centre of the stroke line
                val rad = (angle * PI / 180f).toFloat()
                val dotX = center.x + radius * cos(rad)
                val dotY = center.y + radius * sin(rad)
                drawCircle(
                    color = indicatorColor,
                    radius = 6.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
                Text(
                    text = String.format("%.2fx", value),
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaybackKnobPreview() {
    MaterialTheme {
        PlaybackKnob(
            label = "SPEED",
            value = 1.25f,
            valueRange = 0.25f..2.0f,
            onValueChange = {},
            onReset = {}
        )
    }
}
