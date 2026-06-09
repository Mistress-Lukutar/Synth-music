package com.synth.synthmusic.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.CollectionType
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import com.synth.synthmusic.ui.components.CollectionCard
import com.synth.synthmusic.ui.components.CollectionCardStyle
import com.synth.synthmusic.ui.components.SongList
import com.synth.synthmusic.ui.library.components.AddToPlaylistDialog
import com.synth.synthmusic.ui.library.components.ArtistListItem
import com.synth.synthmusic.ui.share.ShareSongSheet
import org.koin.androidx.compose.koinViewModel

/**
 * Main library screen with tabs for Home, Artists, Genres, and Search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToPlaylistDetail: (Long) -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    onNavigateToAlbumDetail: (String, String) -> Unit,
    onNavigateToArtistDetail: (String) -> Unit,
    onNavigateToGenreDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.currentPlayback.collectAsStateWithLifecycle()
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
        modifier = modifier,
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
                }
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
                    LibraryTab.Home -> HomeTab(
                        recentCollections = uiState.recentCollections,
                        onNavigateToAlbumDetail = onNavigateToAlbumDetail,
                        onNavigateToArtistDetail = onNavigateToArtistDetail,
                        onNavigateToPlaylistDetail = onNavigateToPlaylistDetail
                    )

                    LibraryTab.Artists -> ArtistsTab(
                        artists = uiState.artists,
                        onArtistClick = { onNavigateToArtistDetail(it.name) }
                    )

                    LibraryTab.Genres -> GenresTab(
                        genres = uiState.genres,
                        onGenreClick = onNavigateToGenreDetail
                    )

                    LibraryTab.Search -> SearchTab(
                        query = uiState.searchQuery,
                        results = uiState.searchResults,
                        currentSongId = playback.currentSongId,
                        onQueryChange = { viewModel.onEvent(LibraryEvent.SearchQueryChanged(it)) },
                        onSongClick = { song -> viewModel.onEvent(LibraryEvent.PlaySong(song.id)) },
                        onNavigateToSongInfo = onNavigateToSongInfo,
                        onNavigateToEditMetadata = onNavigateToEditMetadata,
                        onAddToPlaylist = { selectedSongForPlaylist = it },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onShare = { selectedSongForShare = it }
                    )
                }
            }
        }
    }

    selectedSongForPlaylist?.let { songId ->
        AddToPlaylistDialog(
            songId = songId,
            onDismiss = { selectedSongForPlaylist = null }
        )
    }

    selectedSongForShare?.let { songId ->
        val song = uiState.searchResults.find { it.id == songId }
        ShareSongSheet(
            song = song,
            onDismiss = { selectedSongForShare = null }
        )
    }
}

@Composable
private fun HomeTab(
    recentCollections: List<RecentlyPlayedCollection>,
    onNavigateToAlbumDetail: (String, String) -> Unit,
    onNavigateToArtistDetail: (String) -> Unit,
    onNavigateToPlaylistDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = remember(recentCollections) {
        recentCollections.groupBy { it.type }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        grouped[CollectionType.ARTIST]?.let { artists ->
            item {
                SectionHeader(title = "Recent Artists")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    itemsIndexed(
                        items = artists,
                        key = { _, collection -> collection.id }
                    ) { _, collection ->
                        CollectionCard(
                            imageModel = collection.artworkUri,
                            title = collection.name,
                            style = CollectionCardStyle.Compact,
                            onClick = { onNavigateToArtistDetail(collection.name) }
                        )
                    }
                }
            }
        }

        grouped[CollectionType.ALBUM]?.let { albums ->
            item {
                SectionHeader(title = "Recent Albums")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    itemsIndexed(
                        items = albums,
                        key = { _, collection -> collection.id }
                    ) { _, collection ->
                        CollectionCard(
                            imageModel = collection.artworkUri,
                            title = collection.name,
                            style = CollectionCardStyle.Compact,
                            onClick = {
                                onNavigateToAlbumDetail(
                                    collection.name,
                                    collection.extra ?: ""
                                )
                            }
                        )
                    }
                }
            }
        }

        grouped[CollectionType.PLAYLIST]?.let { playlists ->
            item {
                SectionHeader(title = "Recent Playlists")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    itemsIndexed(
                        items = playlists,
                        key = { _, collection -> collection.id }
                    ) { _, collection ->
                        CollectionCard(
                            imageModel = collection.artworkUri,
                            title = collection.name,
                            style = CollectionCardStyle.Compact,
                            onClick = {
                                onNavigateToPlaylistDetail(collection.identifier.toLong())
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
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
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
private fun GenresTab(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(genres, key = { it }) { genre ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGenreClick(genre) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = genre,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SearchTab(
    query: String,
    results: List<com.synth.synthmusic.domain.model.Song>,
    currentSongId: String?,
    onQueryChange: (String) -> Unit,
    onSongClick: (com.synth.synthmusic.domain.model.Song) -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onPlayNext: (String) -> Unit,
    onAddToQueue: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search songs, artists, albums...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true
        )
        SongList(
            songs = results,
            currentSongId = currentSongId,
            onSongClick = onSongClick,
            onNavigateToSongInfo = onNavigateToSongInfo,
            onNavigateToEditMetadata = onNavigateToEditMetadata,
            onAddToPlaylist = onAddToPlaylist,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onShare = onShare,
            modifier = Modifier.fillMaxSize()
        )
    }
}
