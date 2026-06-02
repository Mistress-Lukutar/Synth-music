package com.synth.synthmusic.ui.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.ui.library.components.AddToPlaylistDialog
import com.synth.synthmusic.ui.library.components.ChangeArtworkDialog
import com.synth.synthmusic.ui.library.components.SongListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Detail screen showing songs inside a specific playlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = koinViewModel { parametersOf(playlistId) }
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    var selectedSongForPlaylist by remember { mutableStateOf<String?>(null) }
    var showArtworkDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                viewModel.updateArtwork(bytes)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Change artwork") },
                            onClick = {
                                menuExpanded = false
                                showArtworkDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${songs.size} tracks")
                    Spacer(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.playAll() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play All")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.shuffleAll() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Shuffle")
                        }
                    }
                }
            }
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongListItem(
                    song = song,
                    onClick = { viewModel.playSongAt(index) },
                    onNavigateToSongInfo = onNavigateToSongInfo,
                    onNavigateToEditMetadata = onNavigateToEditMetadata,
                    onAddToPlaylist = { selectedSongForPlaylist = it },
                    onPlayNext = { viewModel.playNext(it) },
                    onAddToQueue = { viewModel.addToQueue(it) },
                    onRemoveFromPlaylist = { viewModel.removeSong(it) },
                    isCurrent = song.id == playback.currentSongId
                )
            }
        }
    }

    selectedSongForPlaylist?.let { songId ->
        AddToPlaylistDialog(
            songId = songId,
            onDismiss = { selectedSongForPlaylist = null }
        )
    }

    if (showArtworkDialog) {
        ChangeArtworkDialog(
            onDismiss = { showArtworkDialog = false },
            onPick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemove = { viewModel.updateArtwork(null) }
        )
    }
}
