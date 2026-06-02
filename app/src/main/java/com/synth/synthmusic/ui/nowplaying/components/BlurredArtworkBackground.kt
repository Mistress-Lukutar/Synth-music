package com.synth.synthmusic.ui.nowplaying.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R

/**
 * Full-screen blurred album-art background with a dark scrim overlay.
 *
 * On API 31+ a real blur is applied via [Modifier.blur].
 * On older devices the artwork is enlarged and dimmed as a fallback.
 *
 * @param artworkUri URI of the artwork to display.
 * @param modifier Modifier for layout.
 */
@Composable
fun BlurredArtworkBackground(
    artworkUri: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = androidx.compose.runtime.remember(artworkUri) {
        ImageRequest.Builder(context)
            .data(artworkUri)
            .placeholder(R.drawable.ic_placeholder_artwork)
            .error(R.drawable.ic_placeholder_artwork)
            .build()
    }

    Box(modifier = modifier.fillMaxSize()) {
        val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }
                .blur(80.dp)
        } else {
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.5f
                    scaleY = 1.5f
                    alpha = 0.35f
                }
        }

        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = blurModifier,
            contentScale = ContentScale.Crop
        )

        // Dark scrim overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.55f)) }
        )
    }
}
