package com.synth.synthmusic.ui.nowplaying.components

import android.media.audiofx.Visualizer
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.ui.theme.SynthMusicTheme
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

internal const val DefaultBarCount = 120

internal const val DefaultSegmentsPerBar = 5
private const val BarWidthRatio = 0.22f
private const val FirstSegmentBrightness = 1.0f
private const val OuterSegmentMaxAlpha = 0.55f
private const val SaturationBoost = 1.5f

// Motion analysis constants.
private const val AttackFactor = 0.45f
private const val ReleaseFactor = 0.12f
private const val BeatDecay = 0.80f
private const val HistoryDecay = 0.995f
private const val MinBarHeight = 0.06f
private const val MaxBarHeight = 1.0f

// Ring layout ratios, measured from the canvas center to its edge.
private const val RingStartRatio = 0.65f
private const val Part1EndRatio = 0.77f
private const val Part2EndRatio = 0.8f
private const val Part3EndRatio = 1.0f

// How much of the accent color is mixed into the bright Part 2 tint.
private const val BrightTintMix = 0.5f

// How much of the accent color is mixed into the inner and outer ring tints.
private const val RingTintMix = 0.8f

// How far the bright Part 2 bars can extend beyond their ring bounds.
// 1.0f fills the ring exactly; values > 1.0f let the bars overlap neighbors.
private const val Part2OverlapFactor = 5f

private val SegmentHeight = 4.dp
private val SegmentGap = 1.dp

/**
 * A circular stacked-bar audio visualizer that wraps around the vinyl disc.
 *
 * The renderer is split into three concentric rings:
 * 1. An inner static ring with a radial transparency gradient.
 * 2. A middle bright ring whose bars pulse with the per-bar energy.
 * 3. An outer animated ring using the original stacked-segment logic.
 *
 * All rings are tinted by mixing white with the accent color. The inner and
 * outer rings use a heavier accent mix, while the middle bright ring uses a
 * lighter mix so it pops more.
 *
 * Only the first color from [barColors] is used as the accent color; when the
 * list is empty the theme primary color is used.
 *
 * @param modifier Modifier for layout.
 * @param audioSessionId Audio session to capture from.
 * @param isPlaying Whether audio is currently playing.
 * @param hasRecordAudioPermission Whether the RECORD_AUDIO permission is granted.
 * @param barColors Optional palette used to tint the bars. Only the first color
 *        is used; falls back to the theme primary color when empty.
 * @param barCount Number of radial columns.
 * @param segmentsPerBar Number of stacked segments in each column.
 */
@Composable
fun RadialBarVisualizer(
    modifier: Modifier = Modifier,
    audioSessionId: Int,
    isPlaying: Boolean,
    hasRecordAudioPermission: Boolean,
    barColors: List<Color> = emptyList(),
    barCount: Int = DefaultBarCount,
    segmentsPerBar: Int = DefaultSegmentsPerBar
) {
    if (!hasRecordAudioPermission) {
        return
    }

    val barHeights = remember {
        mutableStateListOf<Float>().apply { repeat(barCount) { add(MinBarHeight) } }
    }
    val analyzer = remember { AudioMotionAnalyzer(barCount) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(audioSessionId, isPlaying, barCount, hasRecordAudioPermission) {
        if (!isPlaying || !hasRecordAudioPermission) {
            for (i in barHeights.indices) {
                barHeights[i] = MinBarHeight
            }
            return@DisposableEffect onDispose { }
        }

        val effectiveSessionId = audioSessionId.takeIf { it != 0 } ?: 0
        Log.d("RadialBarVisualizer", "Creating Visualizer for session=$audioSessionId, effective=$effectiveSessionId")
        val visualizer = try {
            Visualizer(effectiveSessionId).apply {
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
                            analyzer.process(fft)
                        }
                    },
                    maxRate,
                    false,
                    true
                )
                enabled = true
                Log.d("RadialBarVisualizer", "Visualizer enabled successfully")
            }
        } catch (e: Exception) {
            Log.e("RadialBarVisualizer", "Failed to create Visualizer", e)
            null
        }

        // Pull analyzed heights onto the main thread synchronized with frame rate.
        val renderJob = coroutineScope.launch {
            while (isActive) {
                withFrameNanos { _ ->
                    analyzer.copyTo(barHeights)
                }
            }
        }

        onDispose {
            Log.d("RadialBarVisualizer", "Releasing Visualizer")
            renderJob.cancel()
            visualizer?.release()
        }
    }

    RadialBarVisualizerCanvas(
        barHeights = barHeights,
        barColors = barColors,
        segmentsPerBar = segmentsPerBar,
        modifier = modifier
    )
}

/**
 * Converts raw FFT data into lively, music-reactive bar heights.
 *
 * The algorithm intentionally prioritizes visual movement over frequency
 * accuracy:
 * - Frequency bins are mapped to bars on a logarithmic scale so that bass
 *   energy does not overwhelm the first few columns.
 * - Each bar normalizes against its own recent peak, allowing every column
 *   around the circle to light up independently.
 * - Rotating waves and a slow noise drift guarantee constant motion even
 *   during sparse musical passages.
 * - Beat detection on the bass band triggers radial pulses that travel
 *   around the ring.
 */
private class AudioMotionAnalyzer(private val barCount: Int) {
    private val lock = Any()

    private val heights = FloatArray(barCount) { MinBarHeight }
    private val targets = FloatArray(barCount) { MinBarHeight }
    private val historyMax = FloatArray(barCount) { 0.01f }
    private val noisePhase = FloatArray(barCount) { Random.nextFloat() * 1000f }

    private var bassAverage = 0f
    private var beatPulse = 0f
    private val startTimeNs = System.nanoTime()

    fun process(fft: ByteArray) {
        val binCount = fft.size / 2
        if (binCount <= 0) return

        val magnitudes = FloatArray(binCount)
        for (i in 0 until binCount) {
            val real = fft[i * 2].toFloat()
            val imag = fft[i * 2 + 1].toFloat()
            magnitudes[i] = hypot(real, imag)
        }

        // Frequency-band energies used to drive intensity and detect beats.
        val bass = averageMagnitude(magnitudes, 0, binCount / 32)
        val lowMid = averageMagnitude(magnitudes, binCount / 32, binCount / 16)
        val mid = averageMagnitude(magnitudes, binCount / 16, binCount / 8)
        val highMid = averageMagnitude(magnitudes, binCount / 8, binCount / 4)
        val high = averageMagnitude(magnitudes, binCount / 4, binCount / 2)

        // Beat detection: a pronounced spike in the bass band above its moving average.
        bassAverage = bassAverage * 0.94f + bass * 0.06f
        val isBeat = bass > bassAverage * 1.6f && bass > 0.08f
        if (isBeat) {
            beatPulse = 1f
        }

        val elapsedSec = (System.nanoTime() - startTimeNs) / 1_000_000_000f
        val energy = (bass * 2f + lowMid * 1.3f + mid * 0.7f + highMid * 0.4f + high * 0.2f)
            .coerceIn(0f, 1f)
        val rotationSpeed = 0.7f + energy * 2.8f

        synchronized(lock) {
            for (i in 0 until barCount) {
                val angle = (i.toFloat() / barCount) * 2f * PI.toFloat()

                // Logarithmically mapped frequency energy for this bar.
                val startBin = logBin(i, barCount, binCount)
                val endBin = logBin(i + 1, barCount, binCount)
                val freqEnergy = maxMagnitude(magnitudes, startBin, endBin)

                // Per-bar normalization so quiet frequency ranges can still move.
                historyMax[i] = max(historyMax[i] * HistoryDecay, freqEnergy)
                val normalized = if (historyMax[i] > 0.001f) {
                    freqEnergy / historyMax[i]
                } else {
                    0f
                }

                // Rotating waves around the circle.
                val wave1 = sin(angle * 3f - elapsedSec * rotationSpeed * 4f)
                val wave2 = sin(angle * 5f + elapsedSec * rotationSpeed * 2.5f)
                val wave3 = sin(angle * 8f - elapsedSec * rotationSpeed * 6f)
                val wave = (wave1 * 0.4f + wave2 * 0.3f + wave3 * 0.2f + 0.9f) / 1.8f

                // Slow per-bar noise drift.
                noisePhase[i] += 0.015f + energy * 0.025f
                val noise = (sin(noisePhase[i]) + 1f) * 0.5f

                // Beat pulse travels around the ring as a wave.
                val beatWave = sin(angle - elapsedSec * rotationSpeed * 3f)
                val beatEffect = beatPulse * (0.55f + beatWave * 0.45f)

                val target = MinBarHeight +
                    normalized * 0.35f +
                    wave * 0.22f +
                    beatEffect * 0.40f +
                    noise * 0.12f
                targets[i] = target.coerceIn(MinBarHeight, MaxBarHeight)
            }

            // Decay the global beat pulse and smooth each bar toward its target.
            beatPulse *= BeatDecay
            for (i in 0 until barCount) {
                val diff = targets[i] - heights[i]
                val factor = if (diff > 0) AttackFactor else ReleaseFactor
                heights[i] = (heights[i] + diff * factor).coerceIn(MinBarHeight, MaxBarHeight)
            }
        }
    }

    fun copyTo(destination: MutableList<Float>) {
        synchronized(lock) {
            for (i in heights.indices) {
                if (i < destination.size) {
                    destination[i] = heights[i]
                }
            }
        }
    }

    private fun averageMagnitude(magnitudes: FloatArray, start: Int, end: Int): Float {
        val from = start.coerceIn(0, magnitudes.size)
        val to = end.coerceIn(from, magnitudes.size)
        if (to <= from) return 0f
        var sum = 0f
        for (i in from until to) {
            sum += magnitudes[i]
        }
        return sum / (to - from)
    }

    private fun maxMagnitude(magnitudes: FloatArray, start: Int, end: Int): Float {
        val from = start.coerceIn(0, magnitudes.size)
        val to = end.coerceIn(from, magnitudes.size)
        var max = 0f
        for (i in from until to) {
            if (magnitudes[i] > max) max = magnitudes[i]
        }
        return max
    }

    /**
     * Maps a bar index to an FFT bin using a logarithmic frequency scale.
     * Lower indices cover fewer low-frequency bins; higher indices cover
     * progressively wider high-frequency ranges.
     */
    private fun logBin(bar: Int, totalBars: Int, totalBins: Int): Int {
        if (bar <= 0) return 0
        if (bar >= totalBars) return totalBins
        val t = bar.toFloat() / totalBars
        val logT = ln(1f + t * 9f) / ln(10f)
        return (logT * totalBins).toInt().coerceIn(0, totalBins)
    }
}

/**
 * Stateless renderer for the radial stacked-bar visualizer.
 *
 * Draws three concentric rings around the center:
 * 1. Static ring with a transparency gradient.
 * 2. Bright energy ring whose bars scale with per-bar magnitude.
 * 3. Animated stacked-segment ring.
 *
 * All rings are tinted by mixing white with the saturated accent color. The
 * inner and outer rings use a heavier accent mix ([RingTintMix]), while the
 * middle bright ring uses a lighter accent mix ([BrightTintMix]).
 *
 * @param barHeights Per-bar magnitude values in the range [0, 1].
 * @param barColors Optional palette used to tint the bars. Only the first color
 *        is used; falls back to the theme primary color when empty.
 * @param segmentsPerBar Number of stacked segments in each column.
 * @param modifier Modifier for layout.
 */
@Composable
internal fun RadialBarVisualizerCanvas(
    barHeights: List<Float>,
    barColors: List<Color>,
    segmentsPerBar: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val segmentHeightPx = with(density) { SegmentHeight.toPx() }
    val segmentGapPx = with(density) { SegmentGap.toPx() }
    val themePrimary = MaterialTheme.colorScheme.primary
    val barCount = barHeights.size

    val accentColor = saturateColor(barColors.firstOrNull() ?: themePrimary, SaturationBoost)
    val brightTint = lerpColor(Color.White, accentColor, BrightTintMix)
    val ringTint = lerpColor(Color.White, accentColor, RingTintMix)

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val halfMin = min(size.width, size.height) / 2f

        val part1Inner = halfMin * RingStartRatio
        val part1Outer = halfMin * Part1EndRatio
        val part2Outer = halfMin * Part2EndRatio
        val part3Outer = halfMin * Part3EndRatio

        // Order matters: Part 2 is drawn last so it can visually overlap
        // Part 1 and Part 3 when its bars grow symmetrically.
        drawStaticRing(
            center = center,
            innerRadius = part1Inner,
            outerRadius = part1Outer,
            barCount = barCount,
            segmentsPerBar = segmentsPerBar,
            segmentHeightPx = segmentHeightPx,
            segmentGapPx = segmentGapPx,
            color = ringTint
        )

        drawAnimatedRing(
            barHeights = barHeights,
            center = center,
            innerRadius = part2Outer,
            outerRadius = part3Outer,
            segmentsPerBar = segmentsPerBar,
            segmentHeightPx = segmentHeightPx,
            segmentGapPx = segmentGapPx,
            color = ringTint
        )

        drawBrightEnergyRing(
            barHeights = barHeights,
            center = center,
            innerRadius = part1Outer,
            outerRadius = part2Outer,
            color = brightTint
        )
    }
}

/**
 * Draws the inner static ring.
 *
 * Each radial column has a fixed number of segments whose alpha increases from
 * transparent near the center to fully opaque at the outer edge of the ring.
 * Segments are distributed evenly so the ring fully spans from [innerRadius]
 * to [outerRadius].
 */
private fun DrawScope.drawStaticRing(
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    barCount: Int,
    segmentsPerBar: Int,
    segmentHeightPx: Float,
    segmentGapPx: Float,
    color: Color
) {
    val ringThickness = outerRadius - innerRadius
    val segmentDepth = segmentHeightPx + segmentGapPx
    val effectiveSegments = min(
        segmentsPerBar,
        (ringThickness / segmentDepth).toInt().coerceAtLeast(1)
    )
    val barWidth = (2f * PI.toFloat() * innerRadius / barCount) * BarWidthRatio
    val cornerRadius = (barWidth / 2f).coerceAtLeast(1f)
    val availableLength = (ringThickness - segmentHeightPx).coerceAtLeast(0f)
    val spacing = if (effectiveSegments > 1) availableLength / (effectiveSegments - 1) else 0f

    for (i in 0 until barCount) {
        val angle = (i.toFloat() / barCount) * 2f * PI.toFloat() - (PI / 2f).toFloat()
        val angleDeg = Math.toDegrees(angle.toDouble()).toFloat()

        for (segment in 0 until effectiveSegments) {
            val radius = innerRadius + segmentHeightPx / 2f + segment * spacing
            val alpha = (segment + 1).toFloat() / effectiveSegments
            drawRadialSegment(
                center = center,
                radius = radius,
                angleDeg = angleDeg,
                width = barWidth,
                height = segmentHeightPx,
                cornerRadius = cornerRadius,
                color = color,
                alpha = alpha
            )
        }
    }
}

/**
 * Draws the middle bright ring.
 *
 * Each bar consists of a single segment centered on the ring's middle radius.
 * At zero energy the bar already spans the full Part 2 ring, so there is no
 * gap between Part 1 and Part 2. As energy increases the bar grows symmetrically
 * both inward and outward from the ring center and may overlap neighboring rings.
 */
private fun DrawScope.drawBrightEnergyRing(
    barHeights: List<Float>,
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    color: Color
) {
    val barCount = barHeights.size
    val ringThickness = outerRadius - innerRadius
    val centerRadius = (innerRadius + outerRadius) / 2f
    val barWidth = (2f * PI.toFloat() * centerRadius / barCount) * BarWidthRatio
    val cornerRadius = (barWidth / 2f).coerceAtLeast(1f)
    val minHalfHeight = ringThickness / 2f
    val maxHalfHeight = (ringThickness / 2f) * Part2OverlapFactor

    for (i in 0 until barCount) {
        val magnitude = barHeights.getOrElse(i) { MinBarHeight }
        val angle = (i.toFloat() / barCount) * 2f * PI.toFloat() - (PI / 2f).toFloat()
        val angleDeg = Math.toDegrees(angle.toDouble()).toFloat()

        val halfHeight = minHalfHeight + magnitude * (maxHalfHeight - minHalfHeight)
        val height = halfHeight * 2f
        drawRadialSegment(
            center = center,
            radius = centerRadius,
            angleDeg = angleDeg,
            width = barWidth,
            height = height,
            cornerRadius = cornerRadius,
            color = color,
            alpha = 1f
        )
    }
}

/**
 * Draws the outer animated ring.
 *
 * The inner segment of every column is always bright. Additional segments light
 * up outward when the corresponding frequency magnitude crosses their threshold.
 * Segments are distributed evenly so the ring fully spans from [innerRadius]
 * to [outerRadius].
 */
private fun DrawScope.drawAnimatedRing(
    barHeights: List<Float>,
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    segmentsPerBar: Int,
    segmentHeightPx: Float,
    segmentGapPx: Float,
    color: Color
) {
    val barCount = barHeights.size
    val ringThickness = outerRadius - innerRadius
    val segmentDepth = segmentHeightPx + segmentGapPx
    val effectiveSegments = min(
        segmentsPerBar,
        (ringThickness / segmentDepth).toInt().coerceAtLeast(1)
    )
    val barWidth = (2f * PI.toFloat() * innerRadius / barCount) * BarWidthRatio
    val cornerRadius = (barWidth / 2f).coerceAtLeast(1f)
    val availableLength = (ringThickness - segmentHeightPx).coerceAtLeast(0f)
    val spacing = if (effectiveSegments > 1) availableLength / (effectiveSegments - 1) else 0f

    for (i in 0 until barCount) {
        val magnitude = barHeights.getOrElse(i) { MinBarHeight }
        val angle = (i.toFloat() / barCount) * 2f * PI.toFloat() - (PI / 2f).toFloat()
        val angleDeg = Math.toDegrees(angle.toDouble()).toFloat()

        for (segment in 0 until effectiveSegments) {
            val alpha = when (segment) {
                0 -> (FirstSegmentBrightness + magnitude * 0.05f).coerceIn(0f, 1f)
                else -> {
                    val threshold = segment.toFloat() / effectiveSegments
                    if (magnitude <= threshold) continue
                    OuterSegmentMaxAlpha
                }
            }

            val radius = innerRadius + segmentHeightPx / 2f + segment * spacing
            drawRadialSegment(
                center = center,
                radius = radius,
                angleDeg = angleDeg,
                width = barWidth,
                height = segmentHeightPx,
                cornerRadius = cornerRadius,
                color = color,
                alpha = alpha
            )
        }
    }
}

/**
 * Draws a single rounded radial segment with a solid color.
 */
private fun DrawScope.drawRadialSegment(
    center: Offset,
    radius: Float,
    angleDeg: Float,
    width: Float,
    height: Float,
    cornerRadius: Float,
    color: Color,
    alpha: Float
) {
    if (alpha <= 0.01f || width <= 0f || height <= 0f) return

    val topLeft = Offset(center.x - width / 2f, center.y - radius - height / 2f)

    rotate(degrees = angleDeg, pivot = center) {
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = topLeft,
            size = Size(width, height),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
    }
}

/**
 * Increases the saturation of a color while preserving its hue and value.
 *
 * @param color Source color.
 * @param factor Multiplier applied to the saturation channel.
 * @return A more saturated version of the input color.
 */
private fun saturateColor(color: Color, factor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = (hsv[1] * factor).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Linearly interpolates between two colors in RGBA space.
 */
private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t
    )
}

/**
 * Preview of the radial stacked-bar visualizer using simulated bar heights.
 */
@Preview(device = "id:pixel_5", name = "Visualizer")
@Composable
private fun RadialBarVisualizerPreview() {
    val barCount = DefaultBarCount
    val barHeights = remember {
        List(barCount) { index ->
            val wave = (sin(index * 0.45f) + 1f) / 2f
            MinBarHeight + wave * 0.85f
        }
    }

    SynthMusicTheme(darkTheme = true) {
        RadialBarVisualizerCanvas(
            barHeights = barHeights,
            barColors = listOf(Color(0xFFFF9A00)),
            segmentsPerBar = DefaultSegmentsPerBar,
            modifier = Modifier.size(260.dp)
        )
    }
}