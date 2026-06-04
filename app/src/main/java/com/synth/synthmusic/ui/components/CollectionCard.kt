package com.synth.synthmusic.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R

/**
 * Visual style for a [CollectionCard].
 */
sealed class CollectionCardStyle {
    /**
     * Square card that fills the available width.
     * The cover image uses a 1:1 aspect ratio and the card displays
     * title, optional subtitle, and optional meta text underneath.
     */
    data object Grid : CollectionCardStyle()

    /**
     * Compact horizontal card with a fixed 120 dp cover image.
     * Only the title is shown underneath; subtitle and meta are ignored.
     */
    data object Compact : CollectionCardStyle()
}

/**
 * Reusable card composable for music collections (albums, playlists, recent items, etc.).
 *
 * @param imageUri URI for the cover image. If null, a placeholder is shown.
 * @param title Primary text (e.g. album or playlist name).
 * @param subtitle Secondary text (e.g. artist name). May be null.
 * @param meta Tertiary text (e.g. track count). May be null.
 * @param style Visual layout of the card.
 * @param onClick Callback invoked when the card is clicked.
 * @param modifier Modifier for styling.
 * @param onLongClick Optional callback invoked on a long-press of the card.
 */
@Composable
fun CollectionCard(
    imageUri: String?,
    title: String,
    subtitle: String? = null,
    meta: String? = null,
    style: CollectionCardStyle = CollectionCardStyle.Grid,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val imageRequest = remember(imageUri) {
        ImageRequest.Builder(context)
            .data(imageUri)
            .placeholder(R.drawable.ic_placeholder_artwork)
            .error(R.drawable.ic_placeholder_artwork)
            .build()
    }

    when (style) {
        is CollectionCardStyle.Grid -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .padding(8.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
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

        is CollectionCardStyle.Compact -> {
            Column(
                modifier = modifier
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = title,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionCardGridPreview() {
    CollectionCard(
        imageUri = null,
        title = "Sample Album",
        subtitle = "Sample Artist",
        meta = "12 tracks",
        style = CollectionCardStyle.Grid,
        onClick = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun CollectionCardCompactPreview() {
    CollectionCard(
        imageUri = null,
        title = "Recent Album",
        style = CollectionCardStyle.Compact,
        onClick = {}
    )
}
