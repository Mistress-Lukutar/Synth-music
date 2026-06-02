package com.synth.synthmusic.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection

/**
 * Horizontal card for a recently played collection (album, artist, or playlist).
 *
 * @param collection Collection to display.
 * @param onClick Callback invoked when the card is clicked.
 * @param modifier Modifier for styling.
 */
@Composable
fun RecentlyPlayedCard(
    collection: RecentlyPlayedCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(end = 12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        val context = LocalContext.current
        val imageRequest = remember(collection.artworkUri) {
            ImageRequest.Builder(context)
                .data(collection.artworkUri)
                .placeholder(R.drawable.ic_placeholder_artwork)
                .error(R.drawable.ic_placeholder_artwork)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = collection.name,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Text(
            text = collection.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
