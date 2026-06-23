package com.synth.synthmusic.ui.nowplaying.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.synth.synthmusic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a small color palette from the given artwork URI.
 *
 * The result is returned on a best-effort basis; if the image cannot be loaded
 * or no colors can be extracted an empty list is returned and the caller should
 * fall back to the theme accent colors.
 *
 * @param context Context used to load the image.
 * @param artworkUri URI of the artwork to extract colors from.
 * @return List of dominant colors extracted from the cover art.
 */
suspend fun extractCoverColors(context: Context, artworkUri: String?): List<Color> =
    withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(artworkUri)
            .placeholder(R.drawable.ic_placeholder_artwork)
            .error(R.drawable.ic_placeholder_artwork)
            .allowHardware(false)
            .build()

        val drawable = try {
            when (val result = context.imageLoader.execute(request)) {
                is SuccessResult -> result.drawable
                is ErrorResult -> result.drawable
            }
        } catch (_: Exception) {
            null
        } ?: return@withContext emptyList()

        val bitmap = drawable.toBitmap()
        val palette = Palette.from(bitmap).generate()

        listOfNotNull(
            palette.vibrantSwatch,
            palette.lightVibrantSwatch,
            palette.darkVibrantSwatch,
            palette.mutedSwatch
        )
            .map { Color(it.rgb) }
            .ifEmpty { emptyList() }
    }

/**
 * Remembers the dominant colors of the current artwork as a Compose state.
 *
 * @param artworkUri URI of the artwork to extract colors from.
 * @return List of dominant colors; empty until the first extraction completes.
 */
@Composable
fun rememberCoverColors(artworkUri: String?): List<Color> {
    val context = androidx.compose.ui.platform.LocalContext.current
    var colors by remember(artworkUri) { mutableStateOf(emptyList<Color>()) }

    LaunchedEffect(artworkUri) {
        colors = extractCoverColors(context, artworkUri)
    }

    return colors
}
