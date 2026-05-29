package com.synth.synthmusic.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.R
import com.synth.synthmusic.ui.library.components.AddToPlaylistDialog
import com.synth.synthmusic.ui.library.components.AlbumGridItem
import com.synth.synthmusic.ui.library.components.ArtistListItem
import com.synth.synthmusic.ui.library.components.FoldersTab
import com.synth.synthmusic.ui.library.components.RecentlyPlayedCard
import com.synth.synthmusic.ui.library.components.SongListItem
import com.synth.synthmusic.ui.playlists.PlaylistsScreen
import com.synth.synthmusic.ui.share.ShareSongSheet
import org.koin.androidx.compose.koinViewModel

/**
 * Main library screen with tabs for Songs, Albums, Artists, Folders, and Playlists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToPlaylistDetail: (Long) -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    onNavigateToAlbumDetail: (String, String) -> Unit,
    onNavigateToArtistDetail: (String) -> Unit,
    onNavigateToFolderDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playback by viewModel.currentPlayback.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var selectedSongForPlaylist by remember { mutableStateOf<String?>(null) }
    var selectedSongForShare by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo_yellow),
                            contentDescription = "Synth",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Library")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rescan Library") },
                                onClick = {
                                    menuExpanded = false
                                    permissionLauncher.launch(audioPermission)
                                },
                                leadingIcon = {
                                    if (uiState.isScanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                        recentSongs = uiState.recentSongs,
                        onSongClick = { viewModel.onEvent(LibraryEvent.PlaySong(it.id)) },
                        onNavigateToSongInfo = onNavigateToSongInfo,
                        onNavigateToEditMetadata = onNavigateToEditMetadata,
                        onAddToPlaylist = { selectedSongForPlaylist = it },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onShare = { selectedSongForShare = it },
                        currentSongId = playback.currentSongId
                    )

                    LibraryTab.Albums -> AlbumsTab(
                        albums = uiState.albums,
                        onAlbumClick = { onNavigateToAlbumDetail(it.title, it.artist) }
                    )

                    LibraryTab.Artists -> ArtistsTab(
                        artists = uiState.artists,
                        onArtistClick = { onNavigateToArtistDetail(it.name) }
                    )

                    LibraryTab.Folders -> FoldersTab(
                        folders = uiState.folders,
                        onFolderClick = { onNavigateToFolderDetail(it) }
                    )

                    LibraryTab.Favorites -> SongsTab(
                        songs = uiState.favoriteSongs,
                        recentSongs = emptyList(),
                        onSongClick = { viewModel.onEvent(LibraryEvent.PlaySong(it.id)) },
                        onNavigateToSongInfo = onNavigateToSongInfo,
                        onNavigateToEditMetadata = onNavigateToEditMetadata,
                        onAddToPlaylist = { selectedSongForPlaylist = it },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onShare = { selectedSongForShare = it },
                        currentSongId = playback.currentSongId
                    )

                    LibraryTab.Top -> SongsTab(
                        songs = uiState.topSongs,
                        recentSongs = emptyList(),
                        onSongClick = { viewModel.onEvent(LibraryEvent.PlaySong(it.id)) },
                        onNavigateToSongInfo = onNavigateToSongInfo,
                        onNavigateToEditMetadata = onNavigateToEditMetadata,
                        onAddToPlaylist = { selectedSongForPlaylist = it },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onShare = { selectedSongForShare = it },
                        currentSongId = playback.currentSongId
                    )

                    LibraryTab.Recent -> SongsTab(
                        songs = uiState.recentSongs,
                        recentSongs = emptyList(),
                        onSongClick = { viewModel.onEvent(LibraryEvent.PlaySong(it.id)) },
                        onNavigateToSongInfo = onNavigateToSongInfo,
                        onNavigateToEditMetadata = onNavigateToEditMetadata,
                        onAddToPlaylist = { selectedSongForPlaylist = it },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onShare = { selectedSongForShare = it },
                        currentSongId = playback.currentSongId
                    )

                    LibraryTab.History -> SongsTab(
                        songs = uiState.historySongs,
                        recentSongs = emptyList(),
                        onSongClick = { viewModel.onEvent(LibraryEvent.PlaySong(it.id)) },
                        onNavigateToSongInfo = onNavigateToSongInfo,
                        onNavigateToEditMetadata = onNavigateToEditMetadata,
                        onAddToPlaylist = { selectedSongForPlaylist = it },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onShare = { selectedSongForShare = it },
                        currentSongId = playback.currentSongId
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

    selectedSongForShare?.let { songId ->
        val song = uiState.songs.find { it.id == songId }
            ?: uiState.favoriteSongs.find { it.id == songId }
            ?: uiState.topSongs.find { it.id == songId }
            ?: uiState.recentSongs.find { it.id == songId }
            ?: uiState.historySongs.find { it.id == songId }
        ShareSongSheet(
            song = song,
            onDismiss = { selectedSongForShare = null }
        )
    }
}

@Composable
private fun SongsTab(
    songs: List<com.synth.synthmusic.domain.model.Song>,
    recentSongs: List<com.synth.synthmusic.domain.model.Song>,
    onSongClick: (com.synth.synthmusic.domain.model.Song) -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onPlayNext: (String) -> Unit,
    onAddToQueue: (String) -> Unit,
    onShare: (String) -> Unit,
    currentSongId: String? = null,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (recentSongs.isNotEmpty()) {
            item {
                SectionHeader(title = "Recently Played", onViewAll = {})
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(recentSongs, key = { it.id }) { song ->
                        RecentlyPlayedCard(
                            song = song,
                            onClick = { onSongClick(song) }
                        )
                    }
                }
            }
        }

        if (songs.isNotEmpty()) {
            item {
                SectionHeader(title = "Songs", onViewAll = {})
            }
            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    onNavigateToSongInfo = onNavigateToSongInfo,
                    onNavigateToEditMetadata = onNavigateToEditMetadata,
                    onAddToPlaylist = onAddToPlaylist,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onShare = onShare,
                    isCurrent = song.id == currentSongId
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onViewAll) {
            Text(
                text = "View all",
                color = MaterialTheme.colorScheme.primary
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
