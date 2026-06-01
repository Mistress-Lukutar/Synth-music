package com.synth.synthmusic.ui.metadata.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R

/**
 * Displays the current artwork and allows editing the artwork URI.
 *
 * @param artworkUri the current artwork URI string.
 * @param onArtworkUriChange callback invoked when the URI changes.
 * @param editable whether the URI field is editable.
 * @param modifier the modifier to be applied to the container.
 */
@Composable
fun ArtworkPicker(
    artworkUri: String?,
    onArtworkUriChange: (String) -> Unit,
    editable: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .aspectRatio(1f)
        ) {
            val context = LocalContext.current
            val imageRequest = remember(artworkUri) {
                ImageRequest.Builder(context)
                    .data(artworkUri)
                    .size(512)
                    .placeholder(R.drawable.ic_placeholder_artwork)
                    .error(R.drawable.ic_placeholder_artwork)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = "Artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (editable) {
            OutlinedTextField(
                value = artworkUri ?: "",
                onValueChange = onArtworkUriChange,
                label = { Text("Artwork URI") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
        } else {
            Text(
                text = artworkUri ?: "No artwork",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
