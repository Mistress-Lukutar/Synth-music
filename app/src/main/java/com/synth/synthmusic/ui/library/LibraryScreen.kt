package com.synth.synthmusic.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.ui.library.components.AddToPlaylistDialog
import com.synth.synthmusic.ui.library.components.AlbumGridItem
import com.synth.synthmusic.ui.library.components.ArtistListItem
import com.synth.synthmusic.ui.library.components.MiniPlayer
import com.synth.synthmusic.ui.library.components.SongListItem
import com.synth.synthmusic.ui.playlists.PlaylistsScreen
import org.koin.androidx.compose.koinViewModel

/**
 * Main library screen with tabs for Songs, Albums, Artists, Folders, and Playlists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToPlaylistDetail: (Long) -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playback by viewModel.currentPlayback.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var selectedSongForPlaylist by remember { mutableStateOf<String?>(null) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onEvent(LibraryEvent.ScanLibrary)
        }
    }

    LaunchedEffect(uiState.scanError) {
        uiState.scanError?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Synth Music") },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    permissionLauncher.launch(audioPermission)
                }
            ) {
                if (uiState.isScanning) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan library")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MiniPlayer(
                song = uiState.songs.find { it.id == playback.currentSongId },
                isPlaying = playback.isPlaying,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.skipNext() },
                onExpand = onNavigateToNowPlaying
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryScrollableTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                edgePadding = 8.dp
            ) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.onEvent(LibraryEvent.SelectTab(tab)) },
                        text = { Text(tab.name) }
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState.selectedTab) {
                    LibraryTab.Songs -> SongsTab(
                        songs = uiState.songs,
                        onSongClick = { viewModel.onEvent(LibraryEvent.PlaySong(it.id)) },
                        onNavigateToSongInfo = onNavigateToSongInfo,
                        onNavigateToEditMetadata = onNavigateToEditMetadata,
                        onAddToPlaylist = { selectedSongForPlaylist = it },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) }
                    )
                    LibraryTab.Albums -> AlbumsTab(
                        albums = uiState.albums,
                        onAlbumClick = { }
                    )
                    LibraryTab.Artists -> ArtistsTab(
                        artists = uiState.artists,
                        onArtistClick = { }
                    )
                    LibraryTab.Folders -> EmptyTab(text = "Folders")
                    LibraryTab.Favorites -> SongsTab(
                        songs = uiState.favoriteSongs,
                        onSongClick = { viewModel.onEvent(LibraryEvent.PlaySong(it.id)) },
                        onNavigateToSongInfo = onNavigateToSongInfo,
                        onNavigateToEditMetadata = onNavigateToEditMetadata,
                        onAddToPlaylist = { selectedSongForPlaylist = it },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) }
                    )
                    LibraryTab.Top -> SongsTab(
                        songs = uiState.topSongs,
                        onSongClick = { viewModel.onEvent(LibraryEvent.PlaySong(it.id)) },
                        onNavigateToSongInfo = onNavigateToSongInfo,
                        onNavigateToEditMetadata = onNavigateToEditMetadata,
                        onAddToPlaylist = { selectedSongForPlaylist = it },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) }
                    )
                    LibraryTab.Recent -> SongsTab(
                        songs = uiState.recentSongs,
                        onSongClick = { viewModel.onEvent(LibraryEvent.PlaySong(it.id)) },
                        onNavigateToSongInfo = onNavigateToSongInfo,
                        onNavigateToEditMetadata = onNavigateToEditMetadata,
                        onAddToPlaylist = { selectedSongForPlaylist = it },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) }
                    )
                    LibraryTab.Playlists -> {
                        PlaylistsScreen(
                            onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    selectedSongForPlaylist?.let { songId ->
        val playlists by viewModel.playlists.collectAsState()
        AddToPlaylistDialog(
            playlists = playlists,
            onConfirm = { playlistId ->
                viewModel.addSongToPlaylist(playlistId, songId)
                selectedSongForPlaylist = null
            },
            onDismiss = { selectedSongForPlaylist = null }
        )
    }
}

@Composable
private fun SongsTab(
    songs: List<com.synth.synthmusic.domain.model.Song>,
    onSongClick: (com.synth.synthmusic.domain.model.Song) -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onPlayNext: (String) -> Unit,
    onAddToQueue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(songs, key = { it.id }) { song ->
            SongListItem(
                song = song,
                onClick = { onSongClick(song) },
                onNavigateToSongInfo = onNavigateToSongInfo,
                onNavigateToEditMetadata = onNavigateToEditMetadata,
                onAddToPlaylist = onAddToPlaylist,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue
            )
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: List<com.synth.synthmusic.domain.model.Album>,
    onAlbumClick: (com.synth.synthmusic.domain.model.Album) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumGridItem(
                album = album,
                onClick = { onAlbumClick(album) }
            )
        }
    }
}

@Composable
private fun ArtistsTab(
    artists: List<com.synth.synthmusic.domain.model.Artist>,
    onArtistClick: (com.synth.synthmusic.domain.model.Artist) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(artists, key = { it.id }) { artist ->
            ArtistListItem(
                artist = artist,
                onClick = { onArtistClick(artist) }
            )
        }
    }
}

@Composable
private fun EmptyTab(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
