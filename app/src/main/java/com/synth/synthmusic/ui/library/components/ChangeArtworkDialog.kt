package com.synth.synthmusic.ui.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.HorizontalDivider
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
import com.synth.synthmusic.ui.components.MenuDialog
import com.synth.synthmusic.ui.components.MenuOptionRow

/**
 * Modal menu for changing a collection's artwork.
 *
 * Built on the same [MenuDialog] shell used by Share, Lyrics, and Queue sheets.
 *
 * @param artworkUri URI of the current artwork, displayed in the header.
 * @param collectionTitle Optional title of the collection (album/artist/playlist name).
 * @param onDismiss Dismiss callback.
 * @param onPick Called when the user chooses to pick from gallery.
 * @param onAuto Called when the user chooses the automatic first-track artwork.
 * @param onGenerate Called when the user chooses to generate an artwork.
 * @param onRemove Called when the user chooses to remove the artwork.
 */
@Composable
fun ChangeArtworkDialog(
    artworkUri: String?,
    collectionTitle: String?,
    onDismiss: () -> Unit,
    onPick: () -> Unit,
    onAuto: () -> Unit,
    onGenerate: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current

    MenuDialog(
        title = "Change artwork",
        onDismiss = onDismiss,
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val imageRequest = remember(artworkUri) {
                    ImageRequest.Builder(context)
                        .data(artworkUri)
                        .placeholder(R.drawable.ic_placeholder_artwork)
                        .error(R.drawable.ic_placeholder_artwork)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Current artwork",
                    modifier = Modifier
                        .size(48.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = collectionTitle ?: "Artwork",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        MenuOptionRow(
            icon = Icons.Outlined.Image,
            label = "Pick from gallery",
            onClick = {
                onPick()
                onDismiss()
            }
        )
        MenuOptionRow(
            icon = Icons.Outlined.AutoAwesome,
            label = "Auto (first track)",
            onClick = {
                onAuto()
                onDismiss()
            }
        )
        MenuOptionRow(
            icon = Icons.Outlined.Brush,
            label = "Generate…",
            onClick = {
                onGenerate()
                onDismiss()
            }
        )
        MenuOptionRow(
            icon = Icons.Outlined.DeleteOutline,
            label = "Remove",
            onClick = {
                onRemove()
                onDismiss()
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
