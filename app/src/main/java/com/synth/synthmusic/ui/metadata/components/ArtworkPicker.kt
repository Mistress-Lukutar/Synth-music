package com.synth.synthmusic.ui.metadata.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * Displays the current artwork and provides actions to change it.
 *
 * @param artworkUri the current artwork URI string (null if none).
 * @param artworkBytes optional pending artwork bytes selected from the gallery.
 * @param editable whether the pick/reset/remove actions are enabled.
 * @param onPick callback to launch the gallery picker.
 * @param onReset callback to reset to the embedded MP3 artwork.
 * @param onRemove callback to remove the artwork.
 * @param modifier the modifier to be applied to the container.
 */
@Composable
fun ArtworkPicker(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    artworkBytes: ByteArray? = null,
    editable: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit,
    onRemove: () -> Unit,
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
            val imageRequest = remember(artworkUri, artworkBytes) {
                ImageRequest.Builder(context)
                    .data(artworkBytes ?: artworkUri)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                TextButton(onClick = onPick) {
                    Text("Gallery")
                }
                TextButton(onClick = onReset) {
                    Text("Reset")
                }
                TextButton(onClick = onRemove) {
                    Text("Remove")
                }
            }
        } else {
            Text(
                text = if (artworkUri == null && artworkBytes == null) "No artwork" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
