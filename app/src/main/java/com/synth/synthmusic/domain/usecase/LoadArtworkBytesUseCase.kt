package com.synth.synthmusic.domain.usecase

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads artwork bytes from a content or file URI.
 *
 * Returns null when the URI is blank, invalid, or cannot be opened.
 */
class LoadArtworkBytesUseCase(private val context: Context) {

    suspend operator fun invoke(uri: String?): ByteArray? = withContext(Dispatchers.IO) {
        if (uri.isNullOrBlank()) return@withContext null
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
        }.getOrNull()
    }
}
