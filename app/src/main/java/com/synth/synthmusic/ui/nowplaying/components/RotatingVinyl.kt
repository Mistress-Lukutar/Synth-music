package com.synth.synthmusic.ui.nowplaying.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.synth.synthmusic.R
import com.synth.synthmusic.ui.theme.SynthMusicTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val VinylGlowWidth = 4.dp
private val VinylBorderWidth = 2.dp
private val VinylArtworkPadding = 8.dp
private val VinylInnerRimWidth = 2.dp

/**
 * Extracts a small palette from the given artwork URI.
 *
 * The result is returned on a best-effort basis; if the image cannot be loaded
 * or no colors can be extracted an empty list is returned and the caller should
 * fall back to the theme accent colors.
 */
private suspend fun extractCoverColors(context: Context, artworkUri: String?): List<Color> =
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

@Composable
private fun rememberCoverColors(artworkUri: String?): List<Color> {
    val context = LocalContext.current
    var colors by remember(artworkUri) { mutableStateOf(emptyList<Color>()) }

    LaunchedEffect(artworkUri) {
        colors = extractCoverColors(context, artworkUri)
    }

    return colors
}

/**
 * A circular artwork disc styled like a vinyl record.
 *
 * The disc rotates continuously while [isPlaying] is true and resumes from the
 * same angle when playback restarts. The cover art drives the outer gradient
 * border and the subtle inner rim, giving the artwork a soft 3D lift against
 * the dark vinyl body.
 *
 * @param artworkUri URI of the artwork to display.
 * @param isPlaying Whether the disc should animate rotation.
 * @param modifier Modifier for layout.
 * @param size Diameter of the disc.
 */
@Composable
fun RotatingVinyl(
    artworkUri: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val context = LocalContext.current
    val imageRequest = remember(artworkUri) {
        ImageRequest.Builder(context)
            .data(artworkUri)
            .placeholder(R.drawable.ic_placeholder_artwork)
            .error(R.drawable.ic_placeholder_artwork)
            .build()
    }

    val coverColors = rememberCoverColors(artworkUri)
    val fallbackColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary
    )
    val gradientColors = remember(coverColors) {
        coverColors.ifEmpty { fallbackColors }
    }

    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val current = rotation.value
                rotation.animateTo(
                    targetValue = current + 360f,
                    animationSpec = tween(durationMillis = 8000, easing = LinearEasing)
                )
            }
        }
    }

    val discSizePx = with(LocalDensity.current) { size.toPx() }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation.value }
            .drawBehind {
                val center = Offset(discSizePx / 2f, discSizePx / 2f)
                val radius = discSizePx / 2f
                val glowPx = VinylGlowWidth.toPx()
                val borderPx = VinylBorderWidth.toPx()
                val bodyRadius = radius - glowPx - borderPx
                val artworkPaddingPx = VinylArtworkPadding.toPx()
                val artworkRadius = bodyRadius - artworkPaddingPx
                val innerRimPx = VinylInnerRimWidth.toPx()

                // Soft outer glow ring derived from the cover palette.
                // It sits just outside the main border so the edge fades instead
                // of ending abruptly.
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = gradientColors
                            .map { it.copy(alpha = 0.25f) }
                            .plus(gradientColors.first().copy(alpha = 0.25f)),
                        center = center
                    ),
                    radius = radius - glowPx / 2f,
                    center = center,
                    style = Stroke(width = glowPx)
                )

                // Main colored gradient border.
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = gradientColors + gradientColors.first(),
                        center = center
                    ),
                    radius = bodyRadius + borderPx / 2f,
                    center = center,
                    style = Stroke(width = borderPx)
                )

                // Dark vinyl body.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2E2E2E),
                            Color(0xFF151515),
                            Color(0xFF050505)
                        ),
                        center = center,
                        radius = bodyRadius
                    ),
                    radius = bodyRadius,
                    center = center
                )

                // Subtle inner drop shadow at the artwork edge to lift the cover
                // above the dark vinyl.
                val shadowInset = 2.dp.toPx()
                val shadowOutset = 3.dp.toPx()
                val shadowStart = (artworkRadius - shadowInset).coerceAtLeast(0f) / bodyRadius
                val shadowEnd = (artworkRadius + shadowOutset) / bodyRadius
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            shadowStart to Color.Transparent,
                            shadowEnd to Color.Black.copy(alpha = 0.3f),
                            1.0f to Color.Transparent
                        ),
                        center = center,
                        radius = bodyRadius
                    ),
                    radius = bodyRadius,
                    center = center
                )

                // Inner rim between the artwork and the black vinyl. It uses the
                // cover colors so the cover appears to bleed gently onto the disc.
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = gradientColors
                            .map { it.copy(alpha = 0.55f) }
                            .plus(gradientColors.first().copy(alpha = 0.55f)),
                        center = center
                    ),
                    radius = artworkRadius + innerRimPx / 2f,
                    center = center,
                    style = Stroke(width = innerRimPx)
                )
            }
            .padding(VinylGlowWidth + VinylBorderWidth + VinylArtworkPadding),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "Album art",
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

@Preview(name = "Rotating Vinyl", showBackground = true)
@Composable
private fun RotatingVinylPreview() {
    SynthMusicTheme(darkTheme = true) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            RotatingVinyl(
                artworkUri = null,
                isPlaying = false,
                size = 280.dp
            )
        }
    }
}
