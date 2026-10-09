package com.synth.synthmusic.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * App-private storage for custom assistant avatar images. Picked images are
 * decoded, downscaled to fit within [MAX_DIMENSION_PX] and copied into
 * `filesDir/assistant_avatars`, so avatars survive the deletion of the
 * original file and photo-picker permission expiry. Only the generated file
 * name is persisted in the database.
 */
class AssistantAvatarStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /**
     * Imports the image at [uri] as a new avatar file.
     *
     * @return the generated file name, or `null` when the image cannot be
     * decoded.
     */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@runCatching null

            val scaled = scaleDown(bitmap)
            val png = bounds.outMimeType?.contains("png") == true
            val compressFormat = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val name = "avatar_${UUID.randomUUID()}.${if (png) "png" else "jpg"}"
            val target = File(dir, name)
            try {
                target.outputStream().use { out ->
                    scaled.compress(compressFormat, JPEG_QUALITY, out)
                }
            } catch (e: Exception) {
                target.delete()
                throw e
            } finally {
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
            name
        }.getOrNull()
    }

    /**
     * Resolves a stored avatar file name to its file; the file may not exist.
     */
    fun fileFor(name: String): File = File(dir, name)

    /**
     * Best-effort delete of a previously imported avatar file. Refuses paths
     * escaping the avatar directory.
     */
    fun delete(name: String?) {
        if (name.isNullOrBlank()) return
        val target = File(dir, name)
        if (target.canonicalPath.startsWith(dir.canonicalPath)) {
            target.delete()
        }
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

    companion object {
        /** Subdirectory of filesDir holding imported avatar images. */
        const val DIRECTORY = "assistant_avatars"

        private const val MAX_DIMENSION_PX = 512
        private const val JPEG_QUALITY = 90
    }
}
