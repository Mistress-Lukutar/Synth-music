package com.synth.synthmusic.ui.nowplaying.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.ui.components.MenuDialog
import com.synth.synthmusic.ui.library.components.SongListItem

/**
 * Dialog displaying the current playback queue.
 *
 * Uses the shared [MenuDialog] shell so it matches the other Now Playing menus.
 *
 * @param queue Current list of songs in the queue.
 * @param currentSongId ID of the currently playing song for highlighting.
 * @param onPlayQueueItem Callback invoked with the index of the tapped song.
 * @param onRemoveFromQueue Callback invoked with the index to remove.
 * @param onClearQueue Callback invoked when the user clears the entire queue.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun QueueDialog(
    queue: List<Song>,
    currentSongId: String?,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    MenuDialog(
        title = "Queue",
        onDismiss = onDismiss,
        titleTrailing = {
            if (queue.isNotEmpty()) {
                TextButton(onClick = onClearQueue) {
                    Text("Clear")
                }
            }
        }
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        if (queue.isEmpty()) {
            Text(
                text = "Queue is empty",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(
                    items = queue,
                    // Use the queue position as the key because the same song
                    // can appear multiple times in the queue (e.g. play next).
                    key = { index, _ -> index }
                ) { index, song ->
                    SongListItem(
                        song = song,
                        onClick = { onPlayQueueItem(index) },
                        isCurrent = song.id == currentSongId,
                        trailingContent = {
                            IconButton(
                                onClick = { onRemoveFromQueue(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
