package com.synth.synthmusic.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.synth.synthmusic.domain.model.AiMessagePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Loads image attachments from content URIs and compresses them once at
 * attach time: downscaled to fit within [MAX_DIMENSION_PX] and re-encoded as
 * JPEG q85, then stored inline as base64 image parts.
 */
class ImageAttachmentLoader(
    private val context: Context
) {

    /**
     * Loads and compresses the image at [uri].
     *
     * @throws IllegalArgumentException when the URI cannot be decoded.
     */
    suspend fun load(uri: Uri): AiMessagePart.Image = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Cannot decode image attachment")
        }

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalArgumentException("Cannot decode image attachment")

        val scaled = scaleDown(bitmap)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        val base64 = android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
        if (scaled !== bitmap) scaled.recycle()
        AiMessagePart.Image(mimeType = "image/jpeg", base64 = base64)
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (max(w, h) / 2 >= MAX_DIMENSION_PX) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= MAX_DIMENSION_PX) return bitmap
        val scale = MAX_DIMENSION_PX.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private companion object {
        const val MAX_DIMENSION_PX = 1024
        const val JPEG_QUALITY = 85
    }
}
