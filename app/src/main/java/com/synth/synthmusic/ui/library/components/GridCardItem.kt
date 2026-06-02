package com.synth.synthmusic.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R

/**
 * Reusable grid card composable for collections (albums, playlists, etc.).
 *
 * @param imageUri URI for the cover image. If null, a placeholder is shown.
 * @param title Primary text (e.g. album or playlist name).
 * @param subtitle Secondary text (e.g. artist name). May be null.
 * @param meta Tertiary text (e.g. track count). May be null.
 * @param onClick Callback invoked when the card is clicked.
 * @param overflowActions Optional composable rendered as a dropdown anchored
 *        to the top-end of the cover image (e.g. rename / delete menu).
 * @param modifier Modifier for styling.
 */
@Composable
fun GridCardItem(
    imageUri: String?,
    title: String,
    subtitle: String?,
    meta: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overflowActions: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val context = LocalContext.current
            val imageRequest = remember(imageUri) {
                ImageRequest.Builder(context)
                    .data(imageUri)
                    .placeholder(R.drawable.ic_placeholder_artwork)
                    .error(R.drawable.ic_placeholder_artwork)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            if (overflowActions != null) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    overflowActions()
                }
            }
        }

        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!meta.isNullOrBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
