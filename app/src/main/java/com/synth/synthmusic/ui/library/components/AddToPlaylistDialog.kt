package com.synth.synthmusic.ui.library.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Self-contained dialog for adding a song to a playlist.
 *
 * Fetches the playlist list internally and performs the add operation
 * without requiring the caller to supply playlists or a confirm callback.
 *
 * @param songId ID of the song to add.
 * @param onDismiss Dismiss callback.
 * @param playlistRepository Injected playlist repository.
 */
@Composable
fun AddToPlaylistDialog(
    songId: String,
    onDismiss: () -> Unit,
    playlistRepository: PlaylistRepository = koinInject()
) {
    val playlists by playlistRepository.observeAllPlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            LazyColumn {
                items(playlists, key = { it.id }) { playlist ->
                    TextButton(
                        onClick = {
                            scope.launch {
                                playlistRepository.addSongToPlaylist(playlist.id, songId)
                            }
                            onDismiss()
                        },
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
