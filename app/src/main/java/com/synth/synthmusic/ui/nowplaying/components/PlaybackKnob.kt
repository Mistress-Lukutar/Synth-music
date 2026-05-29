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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A rotary knob control for adjusting a continuous value.
 *
 * Drag vertically to change the value (up = increase, down = decrease).
 * Long-press to reset to the default value.
 *
 * @param label Label displayed above the value (e.g. "SPEED").
 * @param value Current value.
 * @param valueRange Allowed range for the value.
 * @param onValueChange Callback invoked while the user drags.
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

    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    val sensitivity = 0.005f // drag pixels to value ratio

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        accumulatedDrag -= dragAmount.y * sensitivity
                        val range = valueRange.endInclusive - valueRange.start
                        val delta = accumulatedDrag * range
                        if (kotlin.math.abs(delta) >= 0.01f) {
                            val newValue = (value + delta)
                                .coerceIn(valueRange.start, valueRange.endInclusive)
                            onValueChange(newValue)
                            accumulatedDrag = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onReset() }
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

                // Indicator dot
                val rad = (angle * PI / 180f).toFloat()
                val dotRadius = radius - strokeWidth / 2f
                val dotX = center.x + dotRadius * cos(rad)
                val dotY = center.y + dotRadius * sin(rad)
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
