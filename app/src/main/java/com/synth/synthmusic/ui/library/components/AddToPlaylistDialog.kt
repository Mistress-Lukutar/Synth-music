package com.synth.synthmusic.ui.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Bottom sheet for adding or removing a song from multiple playlists.
 *
 * Displays each playlist as a row with cover art, name, and a checkbox.
 * Toggling the checkbox immediately adds or removes the song from that playlist.
 * An OK button dismisses the sheet.
 *
 * @param songId ID of the song to manage.
 * @param onDismiss Dismiss callback.
 * @param playlistRepository Injected playlist repository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    songId: String,
    onDismiss: () -> Unit,
    playlistRepository: PlaylistRepository = koinInject()
) {
    val playlists by playlistRepository.observeAllPlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val containmentMap = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(playlists, songId) {
        playlists.forEach { playlist ->
            containmentMap[playlist.id] = playlistRepository.isSongInPlaylist(playlist.id, songId)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Add to Playlists",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(playlists, key = { it.id }) { playlist ->
                    val isContained = containmentMap[playlist.id] ?: false

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(null)
                                .crossfade(true)
                                .placeholder(R.drawable.ic_placeholder_artwork)
                                .error(R.drawable.ic_placeholder_artwork)
                                .build(),
                            contentDescription = playlist.name,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = isContained,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    if (checked) {
                                        playlistRepository.addSongToPlaylist(playlist.id, songId)
                                    } else {
                                        playlistRepository.removeSongFromPlaylist(playlist.id, songId)
                                    }
                                    containmentMap[playlist.id] = checked
                                }
                            }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text("OK")
                }
            }
        }
    }
}
