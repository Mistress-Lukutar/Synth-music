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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.synth.synthmusic.ui.components.SongList
import com.synth.synthmusic.ui.components.CollectionCard
import com.synth.synthmusic.ui.components.CollectionCardStyle
import com.synth.synthmusic.ui.library.components.AddToPlaylistDialog
import com.synth.synthmusic.ui.library.components.ArtistListItem
import com.synth.synthmusic.ui.library.components.SongListItem
import com.synth.synthmusic.ui.share.ShareSongSheet
import org.koin.androidx.compose.koinViewModel

/**
 * Main library screen with tabs for Queue, Artists, Top, and History.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToPlaylistDetail: (Long) -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    onNavigateToAlbumDetail: (String, String) -> Unit,
    onNavigateToArtistDetail: (String) -> Unit,
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
                    LibraryTab.Queue -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            if (uiState.recentCollections.isNotEmpty()) {
                                item {
                                    SectionHeader(title = "Recently Played")
                                }
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        itemsIndexed(
                                            uiState.recentCollections,
                                            key = { _, collection -> collection.id }
                                        ) { _, collection ->
                                            CollectionCard(
                                                imageModel = collection.artworkUri,
                                                title = collection.name,
                                                style = CollectionCardStyle.Compact,
                                                onClick = {
                                                    when (collection.type) {
                                                        com.synth.synthmusic.domain.model.CollectionType.ALBUM ->
                                                            onNavigateToAlbumDetail(collection.name, collection.extra ?: "")
                                                        com.synth.synthmusic.domain.model.CollectionType.ARTIST ->
                                                            onNavigateToArtistDetail(collection.name)
                                                        com.synth.synthmusic.domain.model.CollectionType.PLAYLIST ->
                                                            onNavigateToPlaylistDetail(collection.identifier.toLong())
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (uiState.queueSongs.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Queue",
                                        actionLabel = "Clear",
                                        onAction = { viewModel.onEvent(LibraryEvent.ClearQueue) }
                                    )
                                }
                                itemsIndexed(
                                    items = uiState.queueSongs,
                                    key = { _, song -> song.id }
                                ) { index, song ->
                                    SongListItem(
                                        song = song,
                                        onClick = { viewModel.playQueueItem(index) },
                                        onNavigateToSongInfo = onNavigateToSongInfo,
                                        onNavigateToEditMetadata = onNavigateToEditMetadata,
                                        onAddToPlaylist = { selectedSongForPlaylist = it },
                                        onPlayNext = { viewModel.playNext(it) },
                                        onAddToQueue = { viewModel.addToQueue(it) },
                                        onShare = { selectedSongForShare = it },
                                        isCurrent = song.id == playback.currentSongId
                                    )
                                }
                            }
                        }
                    }

                    LibraryTab.Artists -> ArtistsTab(
                        artists = uiState.artists,
                        onArtistClick = { onNavigateToArtistDetail(it.name) }
                    )

                    LibraryTab.Top -> SongList(
                        songs = uiState.topSongs,
                        currentSongId = playback.currentSongId,
                        onSongClick = { song -> viewModel.onEvent(LibraryEvent.PlaySong(song.id)) },
                        onNavigateToSongInfo = onNavigateToSongInfo,
                        onNavigateToEditMetadata = onNavigateToEditMetadata,
                        onAddToPlaylist = { selectedSongForPlaylist = it },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onShare = { selectedSongForShare = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    LibraryTab.History -> SongList(
                        songs = uiState.historySongs,
                        currentSongId = playback.currentSongId,
                        onSongClick = { song -> viewModel.onEvent(LibraryEvent.PlaySong(song.id)) },
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
        }
    }

    selectedSongForPlaylist?.let { songId ->
        AddToPlaylistDialog(
            songId = songId,
            onDismiss = { selectedSongForPlaylist = null }
        )
    }

    selectedSongForShare?.let { songId ->
        val song = uiState.queueSongs.find { it.id == songId }
            ?: uiState.topSongs.find { it.id == songId }
            ?: uiState.historySongs.find { it.id == songId }
        ShareSongSheet(
            song = song,
            onDismiss = { selectedSongForShare = null }
        )
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
