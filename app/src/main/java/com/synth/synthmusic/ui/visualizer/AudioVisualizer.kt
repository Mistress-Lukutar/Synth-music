package com.synth.synthmusic.ui.visualizer

import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.MaterialTheme

/**
 * Renders a real-time frequency-bar audio visualizer tied to an audio session.
 *
 * @param audioSessionId Audio session to capture from (e.g. ExoPlayer.audioSessionId).
 * @param barCount Number of frequency bars to draw.
 * @param color Bar color.
 * @param modifier Modifier for layout.
 */
@Composable
fun AudioVisualizer(
    audioSessionId: Int,
    barCount: Int = 48,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    if (audioSessionId == 0) {
        return
    }

    val barHeights = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0f) } } }

    DisposableEffect(audioSessionId, barCount) {
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
                            val groupSize = (n / barCount).coerceAtLeast(1)
                            for (i in 0 until barCount) {
                                var sum = 0f
                                for (j in 0 until groupSize) {
                                    val idx = i * groupSize + j
                                    if (idx < n) {
                                        val real = fft[idx * 2].toFloat()
                                        val imag = fft[idx * 2 + 1].toFloat()
                                        val magnitude = kotlin.math.hypot(real, imag)
                                        sum += magnitude
                                    }
                                }
                                val avg = sum / groupSize
                                barHeights[i] = (avg / 128f).coerceIn(0f, 1f)
                            }
                        }
                    },
                    maxRate,
                    false, // waveform
                    true   // fft
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

    Canvas(modifier = modifier.fillMaxSize()) {
        val barWidth = size.width / barCount
        val gap = barWidth * 0.2f
        val drawWidth = barWidth - gap

        for (i in 0 until barCount) {
            val barH = barHeights.getOrElse(i) { 0f } * size.height
            val x = i * barWidth + gap / 2
            val y = size.height - barH
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(drawWidth, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(drawWidth / 2, drawWidth / 2)
            )
        }
    }
}
