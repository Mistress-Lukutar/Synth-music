package com.synth.synthmusic.ui.library.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.domain.model.Playlist

/**
 * Dialog for picking a playlist to add a song to.
 *
 * @param playlists Available playlists.
 * @param onConfirm Callback with selected playlist ID.
 * @param onDismiss Dismiss callback.
 */
@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            LazyColumn {
                items(playlists, key = { it.id }) { playlist ->
                    TextButton(
                        onClick = { onConfirm(playlist.id); onDismiss() },
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(playlist.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
