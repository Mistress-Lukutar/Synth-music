package com.synth.synthmusic.ui.nowplaying.components

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R
import com.synth.synthmusic.ui.theme.SynthMusicTheme

/**
 * Now Playing background that centers the blurred artwork on the playback disc
 * and scales it so the top edge reaches the top of the screen.
 *
 * The artwork is faded into the theme's background color at the top and bottom
 * so the status bar and playback controls remain readable.
 *
 * @param artworkUri URI of the artwork to display.
 * @param artworkCenterY Vertical center of the disc in window coordinates, used
 *                       to align and scale the artwork. Zero means not measured yet.
 * @param overlayStrength Opacity of the background color overlay blended on top
 *                        of the artwork, from `0` (no overlay) to `1` (fully tinted).
 * @param modifier Modifier for layout.
 */
@Composable
fun BlurredArtworkBackground(
    modifier: Modifier = Modifier,
    artworkUri: String?,
    artworkCenterY: Float = 0f,
    overlayStrength: Float = 0.5f
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageRequest = remember(artworkUri) {
        ImageRequest.Builder(context)
            .data(artworkUri)
            .placeholder(R.drawable.ic_placeholder_artwork)
            .error(R.drawable.ic_placeholder_artwork)
            .build()
    }

    // Fallback size used while the real artwork has not reported its intrinsic size.
    val coverSize = 280.dp
    val coverSizePx = with(density) { coverSize.toPx() }

    // Blur radius applied to the background artwork.
    val blurRadius = 10.dp

    val backgroundColor = MaterialTheme.colorScheme.background
    val overlayColorFilter = remember(backgroundColor, overlayStrength) {
        ColorFilter.tint(
            color = backgroundColor.copy(alpha = overlayStrength.coerceIn(0f, 1f)),
            blendMode = BlendMode.SrcOver
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        val boxHeightPx = with(density) { maxHeight.toPx() }
        val measured = artworkCenterY > 0f && boxHeightPx > 0f

        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            success = {
                val painter = painter
                val intrinsicSize = painter.intrinsicSize
                val intrinsicHeight = if (intrinsicSize.height > 0f) {
                    intrinsicSize.height
                } else {
                    0f
                }

                // Scale the artwork so its height equals 2 * discCenterY,
                // which places the top edge at y = 0 and the bottom edge at
                // 2 * discCenterY. This keeps the artwork centered on the disc.
                val scale = if (measured && intrinsicHeight > 0f) {
                    (artworkCenterY * 2f / intrinsicHeight).coerceAtLeast(1f)
                } else if (measured) {
                    (artworkCenterY * 2f / coverSizePx).coerceAtLeast(1f)
                } else {
                    1f
                }

                // A fillMaxSize() Image is drawn centered in the box.
                // Move the artwork center from the box center to the disc center.
                val translationY = if (measured) {
                    artworkCenterY - boxHeightPx / 2f
                } else {
                    0f
                }

                Image(
                    painter = painter,
                    contentDescription = null,
                    alignment = Alignment.Center,
                    contentScale = ContentScale.None,
                    colorFilter = overlayColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.translationY = translationY
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                alpha = 0.35f
                            }
                        }
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(blurRadius)
                            } else {
                                Modifier
                            }
                        )
                )
            },
            loading = {
                // Show the placeholder full screen while the real artwork loads.
                Image(
                    painter = painter,
                    contentDescription = null,
                    alignment = Alignment.Center,
                    contentScale = ContentScale.Crop,
                    colorFilter = overlayColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(blurRadius)
                            } else {
                                Modifier.graphicsLayer { alpha = 0.35f }
                            }
                        )
                )
            },
            error = {
                // Show the error placeholder full screen.
                Image(
                    painter = painter,
                    contentDescription = null,
                    alignment = Alignment.Center,
                    contentScale = ContentScale.Crop,
                    colorFilter = overlayColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(blurRadius)
                            } else {
                                Modifier.graphicsLayer { alpha = 0.35f }
                            }
                        )
                )
            }
        )

        // Gradient overlays blended over the whole background.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val coverBottomPx = artworkCenterY * 2f
                    val coverBottomRatio = (coverBottomPx / boxHeightPx).coerceIn(0f, 1f)
                    // Start fading well before the artwork ends so the transition
                    // to the solid background is smooth rather than a hard edge.
                    val bottomFadeStart = (coverBottomRatio - 0.35f).coerceAtLeast(0.25f)
                    val bottomFadeEnd = (coverBottomRatio + 0.15f).coerceAtMost(1f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to backgroundColor,
                                0.2f to Color.Transparent,
                                bottomFadeStart to Color.Transparent,
                                bottomFadeEnd to backgroundColor,
                                1.0f to backgroundColor
                            ),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                }
        )
    }
}

@Preview(device = "id:pixel_5", name = "Light")
@Preview(device = "id:pixel_5", name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BlurredArtworkBackgroundPreview() {
    SynthMusicTheme {
        val context = LocalContext.current
        val density = LocalDensity.current
        val artworkUri = "android.resource://${context.packageName}/drawable/ic_placeholder_artwork"
        val artworkCenterY = with(density) { 320.dp.toPx() }
        BlurredArtworkBackground(
            artworkUri = artworkUri,
            artworkCenterY = artworkCenterY
        )
    }
}
