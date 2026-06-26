package com.synth.synthmusic.domain.usecase

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.synth.synthmusic.domain.model.GeneratedArtworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Generates an abstract square artwork image from a configuration.
 *
 * The result is a 1024x1024 JPEG with a gradient background, soft abstract
 * overlays, and a centered icon whose position and scale are user-controlled.
 */
class GenerateArtworkUseCase {

    /**
     * Generates the artwork image.
     *
     * @param config User-selected colors, icon, and geometry.
     * @param size Output bitmap size in pixels. Defaults to a full-resolution cover.
     */
    suspend operator fun invoke(
        config: GeneratedArtworkConfig,
        size: Int = DEFAULT_SIZE
    ): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas, size, config.backgroundStartColor, config.backgroundEndColor)
        drawAbstractOverlays(canvas, size, config.backgroundStartColor, config.backgroundEndColor)
        drawIcon(canvas, size, config)

        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            stream.toByteArray()
        }
    }

    private fun drawBackground(canvas: Canvas, size: Int, startColor: Color, endColor: Color) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                startColor.toArgb(),
                endColor.toArgb(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    }

    private fun drawAbstractOverlays(canvas: Canvas, size: Int, startColor: Color, endColor: Color) {
        val blend = blendColors(startColor, endColor, 0.5f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blend.toArgb()
            alpha = 50
        }

        // Large soft circle in the top-left quadrant.
        canvas.drawCircle(size * 0.25f, size * 0.2f, size * 0.55f, paint)

        // Large soft circle in the bottom-right quadrant.
        paint.alpha = 40
        canvas.drawCircle(size * 0.75f, size * 0.85f, size * 0.6f, paint)

        // Soft accent oval crossing the center.
        paint.alpha = 30
        val oval = RectF(
            size * -0.1f,
            size * 0.45f,
            size * 1.1f,
            size * 0.65f
        )
        canvas.drawOval(oval, paint)
    }

    private fun drawIcon(canvas: Canvas, size: Int, config: GeneratedArtworkConfig) {
        val path = config.icon.imageVector.toAndroidPath()
        val bounds = RectF()
        path.computeBounds(bounds, true)

        val pathWidth = bounds.width()
        val pathHeight = bounds.height()
        if (pathWidth <= 0f || pathHeight <= 0f) return

        val baseIconSize = size * DEFAULT_ICON_SCALE
        val iconSize = baseIconSize * config.iconScale.coerceIn(MIN_SCALE, MAX_SCALE)

        // Fit the vector inside a square box of `iconSize`, preserving aspect ratio.
        val scale = iconSize / max(pathWidth, pathHeight)

        // 0% places the icon's bounding box at the start edge, 100% at the end edge.
        val translateX = config.iconOffsetXPercent * (size - iconSize)
        val translateY = config.iconOffsetYPercent * (size - iconSize)

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(
                translateX - bounds.left * scale,
                translateY - bounds.top * scale
            )
        }
        path.transform(matrix)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.iconColor.toArgb()
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)
    }

    private fun blendColors(a: Color, b: Color, ratio: Float): Color {
        return Color(
            red = a.red + (b.red - a.red) * ratio,
            green = a.green + (b.green - a.green) * ratio,
            blue = a.blue + (b.blue - a.blue) * ratio,
            alpha = a.alpha + (b.alpha - a.alpha) * ratio
        )
    }

    companion object {
        private const val DEFAULT_SIZE = 1024
        private const val JPEG_QUALITY = 92
        private const val DEFAULT_ICON_SCALE = 0.4f
        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 2.0f
    }
}
