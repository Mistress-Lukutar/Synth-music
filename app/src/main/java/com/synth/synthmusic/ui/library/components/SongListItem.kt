package com.synth.synthmusic.ui.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.synth.synthmusic.domain.model.Song

/**
 * List item composable for a single song.
 *
 * @param song Song to display.
 * @param onClick Callback invoked when the item is clicked.
 * @param onNavigateToSongInfo Callback to open song info.
 * @param onNavigateToEditMetadata Callback to open metadata editor.
 * @param onAddToPlaylist Callback to add song to a playlist.
 * @param onPlayNext Callback to play this song next in the queue.
 * @param onAddToQueue Callback to append this song to the queue.
 * @param onShare Callback to share the song.
 * @param onRemoveFromPlaylist Callback to remove the song from the current playlist.
 * @param isCurrent Whether this song is the currently playing track.
 * @param modifier Modifier for styling.
 */
@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    onNavigateToSongInfo: ((String) -> Unit)? = null,
    onNavigateToEditMetadata: ((String) -> Unit)? = null,
    onAddToPlaylist: ((String) -> Unit)? = null,
    onPlayNext: ((String) -> Unit)? = null,
    onAddToQueue: ((String) -> Unit)? = null,
    onShare: ((String) -> Unit)? = null,
    onRemoveFromPlaylist: ((String) -> Unit)? = null,
    isCurrent: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent) {
            TinyEqualizer(modifier = Modifier.padding(end = 8.dp))
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.artworkUri)
                .crossfade(true)
                .placeholder(R.drawable.ic_placeholder_artwork)
                .error(R.drawable.ic_placeholder_artwork)
                .build(),
            contentDescription = "Album art",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatDuration(song.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp)
        )
        val hasMenu = listOf(
            onNavigateToSongInfo,
            onNavigateToEditMetadata,
            onPlayNext,
            onAddToQueue,
            onShare,
            onAddToPlaylist
        ).any { it != null }

        if (hasMenu) {
            Box {
                IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    onNavigateToSongInfo?.let { callback ->
                        DropdownMenuItem(
                            text = { Text("Song Info") },
                            onClick = { expanded = false; callback(song.id) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                        )
                    }
                    onNavigateToEditMetadata?.let { callback ->
                        DropdownMenuItem(
                            text = { Text("Edit Metadata") },
                            onClick = { expanded = false; callback(song.id) }
                        )
                    }
                    onPlayNext?.let { callback ->
                        DropdownMenuItem(
                            text = { Text("Play Next") },
                            onClick = { expanded = false; callback(song.id) }
                        )
                    }
                    onAddToQueue?.let { callback ->
                        DropdownMenuItem(
                            text = { Text("Add to Queue") },
                            onClick = { expanded = false; callback(song.id) }
                        )
                    }
                    onShare?.let { callback ->
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = { expanded = false; callback(song.id) }
                        )
                    }
                    onAddToPlaylist?.let { callback ->
                        DropdownMenuItem(
                            text = { Text("Add to Playlist") },
                            onClick = { expanded = false; callback(song.id) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Default.PlaylistAdd, contentDescription = null) }
                        )
                    }
                }
            }
        }

        onRemoveFromPlaylist?.let { callback ->
            IconButton(onClick = { callback(song.id) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun TinyEqualizer(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.size(width = 12.dp, height = 16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val barColor = MaterialTheme.colorScheme.primary
        listOf(4.dp, 8.dp, 6.dp).forEach { height ->
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .padding(end = 2.dp)
                    .size(width = 2.dp, height = height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
