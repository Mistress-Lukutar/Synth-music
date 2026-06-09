package com.synth.synthmusic.ui.library

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.CollectionType
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import com.synth.synthmusic.ui.components.CollectionCard
import com.synth.synthmusic.ui.components.CollectionCardStyle
import com.synth.synthmusic.ui.library.components.ArtistListItem
import org.koin.androidx.compose.koinViewModel

/**
 * Main library screen with tabs for Home, Artists, and Genres.
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
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                }
            }
        }
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
            androidx.compose.material3.TextButton(onClick = onAction) {
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
