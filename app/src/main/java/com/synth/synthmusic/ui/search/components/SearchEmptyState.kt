package com.synth.synthmusic.ui.search.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.CollectionType
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import com.synth.synthmusic.ui.components.CollectionCard
import com.synth.synthmusic.ui.components.CollectionCardStyle

/**
 * Section displaying recent search queries from history.
 *
 * Renders a header row with a "Clear all" action (visible only when there are entries),
 * followed by a list of clickable history items, each with a remove button.
 *
 * @param recentSearches List of recent search query strings.
 * @param onSelect Callback invoked when a history item is tapped.
 * @param onRemove Callback invoked to remove a specific history entry.
 * @param onClearAll Callback invoked to clear all history entries.
 * @param modifier Modifier for styling.
 */
@Composable
fun RecentSearchesSection(
    recentSearches: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (recentSearches.isEmpty()) return

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            if (recentSearches.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear all")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        recentSearches.forEach { query ->
            ListItem(
                headlineContent = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    Icon(Icons.Default.History, contentDescription = null)
                },
                trailingContent = {
                    IconButton(onClick = { onRemove(query) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove from history")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(query) }
            )
        }
    }
}

/**
 * Horizontal row of recently played album and artist collections.
 *
 * Renders a header followed by a horizontal scrollable list of compact collection cards.
 * Tapping an album card navigates to album detail; tapping an artist card navigates
 * to the artist detail screen.
 *
 * @param recentCollections List of recently played collections (albums and artists only).
 * @param onNavigateToAlbumDetail Callback for album navigation with title and artist.
 * @param onNavigateToArtistDetail Callback for artist navigation by name.
 * @param modifier Modifier for styling.
 */
@Composable
fun RecentCollectionsRow(
    recentCollections: List<RecentlyPlayedCollection>,
    onNavigateToAlbumDetail: (String, String) -> Unit,
    onNavigateToArtistDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (recentCollections.isEmpty()) return

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Recently played",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            items(recentCollections, key = { it.id }) { collection ->
                CollectionCard(
                    modifier = Modifier,
                    imageModel = collection.artworkUri,
                    title = collection.name,
                    style = CollectionCardStyle.Compact,
                    onClick = {
                        when (collection.type) {
                            CollectionType.ALBUM -> onNavigateToAlbumDetail(collection.name, collection.extra ?: "")
                            CollectionType.ARTIST -> onNavigateToArtistDetail(collection.name)
                            CollectionType.PLAYLIST -> Unit
                        }
                    }
                )
            }
        }
    }
}

/**
 * Centered empty-state content shown when a search query yields no results.
 *
 * Displays a large search icon, the queried term, and a suggestion to check spelling.
 *
 * @param query The search query that produced no results.
 * @param modifier Modifier for styling.
 */
@Composable
fun NoResultsContent(
    query: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No results for \"$query\"",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Check the spelling or try a different term",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
