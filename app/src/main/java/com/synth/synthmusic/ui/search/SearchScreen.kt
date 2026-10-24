package com.synth.synthmusic.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.ui.library.components.AddToPlaylistDialog
import com.synth.synthmusic.ui.search.components.NoResultsContent
import com.synth.synthmusic.ui.search.components.RecentCollectionsRow
import com.synth.synthmusic.ui.search.components.RecentSearchesSection
import com.synth.synthmusic.ui.search.components.SearchResultSections
import com.synth.synthmusic.ui.search.components.SearchTopBar
import com.synth.synthmusic.ui.share.ShareSongSheet
import org.koin.androidx.compose.koinViewModel

/**
 * Search screen displayed as the third tab of the bottom navigation.
 *
 * Shows a search bar in the top app bar, recent searches and recently played collections
 * when idle, and categorized search results when a query is active. Supports song playback,
 * queue management, playlist addition, and sharing.
 *
 * @param onNavigateToSongInfo Callback to navigate to song info (song ID).
 * @param onNavigateToAlbumDetail Callback to navigate to album detail (title, artist).
 * @param onNavigateToArtistDetail Callback to navigate to artist detail (name).
 * @param onNavigateToSettings Callback to navigate to settings.
 * @param modifier Modifier for styling.
 * @param viewModel The search ViewModel providing state and events.
 */
@Composable
fun SearchScreen(
    onNavigateToSongInfo: (String) -> Unit,
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
            SearchTopBar(
                query = uiState.query,
                onQueryChange = { viewModel.onEvent(SearchEvent.QueryChanged(it)) },
                onNavigateToSettings = onNavigateToSettings
            )
        }
    ) { innerPadding ->
        if (uiState.isQueryBlank) {
            // Idle state: show recent searches and recently played collections.
            if (uiState.recentSearches.isEmpty() && uiState.recentCollections.isEmpty()) {
                SearchIdleHint(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                ) {
                    if (uiState.recentSearches.isNotEmpty()) {
                        RecentSearchesSection(
                            recentSearches = uiState.recentSearches,
                            onSelect = { query ->
                                viewModel.onEvent(SearchEvent.HistorySelected(query))
                            },
                            onRemove = { query ->
                                viewModel.onEvent(SearchEvent.RemoveHistoryEntry(query))
                            },
                            onClearAll = {
                                viewModel.onEvent(SearchEvent.ClearHistory)
                            }
                        )
                    }
                    if (uiState.recentCollections.isNotEmpty()) {
                        RecentCollectionsRow(
                            recentCollections = uiState.recentCollections,
                            onNavigateToAlbumDetail = onNavigateToAlbumDetail,
                            onNavigateToArtistDetail = onNavigateToArtistDetail
                        )
                    }
                }
            }
        } else if (uiState.hasResults) {
            // Active search with matches: show categorized results.
            SearchResultSections(
                songs = uiState.songs,
                albums = uiState.albums,
                artists = uiState.artists,
                currentSongId = uiState.currentSongId,
                onSongClick = { song -> viewModel.onEvent(SearchEvent.PlaySong(song.id)) },
                onNavigateToSongInfo = onNavigateToSongInfo,
                onAddToPlaylist = { selectedSongForPlaylist = it },
                onPlayNext = { viewModel.playNext(it) },
                onAddToQueue = { viewModel.addToQueue(it) },
                onShare = { selectedSongForShare = it },
                onNavigateToAlbumDetail = onNavigateToAlbumDetail,
                onNavigateToArtistDetail = onNavigateToArtistDetail,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            // Active search with no matches.
            NoResultsContent(
                query = uiState.query,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
        val song = uiState.songs.find { it.id == songId }
        ShareSongSheet(
            song = song,
            onDismiss = { selectedSongForShare = null }
        )
    }
}

/**
 * Neutral hint shown when no recent searches or collections are available.
 *
 * @param modifier Modifier for styling.
 */
@Composable
private fun SearchIdleHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Start typing to search your library",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
