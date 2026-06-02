package com.synth.synthmusic.ui.nowplaying.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R
import kotlinx.coroutines.isActive

/**
 * A circular artwork disc styled like a vinyl record.
 *
 * The disc rotates continuously while [isPlaying] is true and resumes from
 * the same angle when playback restarts.
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
    size: androidx.compose.ui.unit.Dp = 280.dp
) {
    val context = LocalContext.current
    val imageRequest = remember(artworkUri) {
        ImageRequest.Builder(context)
            .data(artworkUri)
            .placeholder(R.drawable.ic_placeholder_artwork)
            .error(R.drawable.ic_placeholder_artwork)
            .build()
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

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation.value }
            .drawBehind {
                // Outer vinyl groove ring
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A1A1A),
                            Color(0xFF0D0D0D)
                        ),
                        radius = size.toPx() / 2f
                    ),
                    radius = size.toPx() / 2f
                )
            }
            .padding(12.dp),
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

        // Small center hole like a vinyl record
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D0D0D))
                .align(Alignment.Center)
        )
    }
}
