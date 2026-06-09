package com.synth.synthmusic.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.ui.components.SongList
import com.synth.synthmusic.ui.library.components.AddToPlaylistDialog
import com.synth.synthmusic.ui.share.ShareSongSheet
import org.koin.androidx.compose.koinViewModel

/**
 * Standalone search screen with query input and results list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    onNavigateToAlbumDetail: (String, String) -> Unit,
    onNavigateToArtistDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedSongForPlaylist by remember { mutableStateOf<String?>(null) }
    var selectedSongForShare by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TextField(
                value = uiState.query,
                onValueChange = { viewModel.onEvent(SearchEvent.QueryChanged(it)) },
                placeholder = { Text("Search songs, artists, albums...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )
            SongList(
                songs = uiState.results,
                currentSongId = uiState.currentSongId,
                onSongClick = { song -> viewModel.onEvent(SearchEvent.PlaySong(song.id)) },
                onNavigateToSongInfo = onNavigateToSongInfo,
                onNavigateToEditMetadata = onNavigateToEditMetadata,
                onAddToPlaylist = { selectedSongForPlaylist = it },
                onPlayNext = { viewModel.playNext(it) },
                onAddToQueue = { viewModel.addToQueue(it) },
                onShare = { selectedSongForShare = it },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    selectedSongForPlaylist?.let { songId ->
        AddToPlaylistDialog(
            songId = songId,
            onDismiss = { selectedSongForPlaylist = null }
        )
    }

    selectedSongForShare?.let { songId ->
        val song = uiState.results.find { it.id == songId }
        ShareSongSheet(
            song = song,
            onDismiss = { selectedSongForShare = null }
        )
    }
}
