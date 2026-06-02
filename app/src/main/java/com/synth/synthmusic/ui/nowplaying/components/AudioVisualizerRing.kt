package com.synth.synthmusic.ui.nowplaying.components

import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A circular ring of dots that reacts to audio FFT data.
 *
 * Dots are arranged around a circle and pulse outward based on the current
 * frequency magnitudes. If the visualizer cannot be initialized, a static
 * decorative ring is drawn.
 *
 * @param audioSessionId Audio session to capture from.
 * @param isPlaying Whether audio is currently playing.
 * @param dotColor Color of the dots.
 * @param modifier Modifier for layout.
 * @param dotCount Number of dots around the ring.
 */
@Composable
fun AudioVisualizerRing(
    audioSessionId: Int,
    isPlaying: Boolean,
    dotColor: Color,
    modifier: Modifier = Modifier,
    dotCount: Int = 64
) {
    val barHeights = remember { mutableStateListOf<Float>().apply { repeat(dotCount) { add(0.05f) } } }

    DisposableEffect(audioSessionId, isPlaying, dotCount) {
        if (audioSessionId == 0 || !isPlaying) {
            // Reset to baseline when not playing
            for (i in barHeights.indices) {
                barHeights[i] = 0.05f
            }
            return@DisposableEffect onDispose { }
        }

        val visualizer = try {
            Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                val maxRate = Visualizer.getMaxCaptureRate()
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            vis: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                        }

                        override fun onFftDataCapture(
                            vis: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            fft ?: return
                            val n = fft.size / 2
                            val groupSize = (n / dotCount).coerceAtLeast(1)
                            for (i in 0 until dotCount) {
                                var sum = 0f
                                for (j in 0 until groupSize) {
                                    val idx = i * groupSize + j
                                    if (idx < n) {
                                        val real = fft[idx * 2].toFloat()
                                        val imag = fft[idx * 2 + 1].toFloat()
                                        val magnitude = hypot(real, imag)
                                        sum += magnitude
                                    }
                                }
                                val avg = sum / groupSize
                                barHeights[i] = ((avg / 128f) * 0.6f + 0.05f).coerceIn(0.05f, 1f)
                            }
                        }
                    },
                    maxRate,
                    false,
                    true
                )
                enabled = true
            }
        } catch (e: Exception) {
            null
        }

        onDispose {
            visualizer?.release()
        }
    }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val baseRadius = (size.width.coerceAtMost(size.height) / 2f) * 0.78f
        val maxExtension = baseRadius * 0.25f
        val baseDotRadius = size.width.coerceAtMost(size.height) * 0.012f

        for (i in 0 until dotCount) {
            val magnitude = barHeights.getOrElse(i) { 0.05f }
            val angle = (i.toFloat() / dotCount) * 2f * kotlin.math.PI - kotlin.math.PI / 2f
            val radius = baseRadius + magnitude * maxExtension
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            val dotRadius = baseDotRadius + magnitude * baseDotRadius * 1.5f

            // Glow layer
            drawDot(x, y, dotRadius * 2.5f, dotColor.copy(alpha = magnitude * 0.25f))
            // Main dot
            drawDot(x, y, dotRadius, dotColor.copy(alpha = 0.8f + magnitude * 0.2f))
        }
    }
}

private fun DrawScope.drawDot(x: Float, y: Float, radius: Float, color: Color) {
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(x, y)
    )
}
