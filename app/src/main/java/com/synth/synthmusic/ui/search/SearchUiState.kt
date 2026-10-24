package com.synth.synthmusic.ui.search

import com.synth.synthmusic.domain.model.Album
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import com.synth.synthmusic.domain.model.Song

/**
 * UI state for the standalone search screen.
 *
 * Holds the current query, filtered catalog results (songs, albums, artists),
 * recent searches from history, recently played collections, and the ID of the
 * currently playing song.
 */
data class SearchUiState(
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val recentCollections: List<RecentlyPlayedCollection> = emptyList(),
    val currentSongId: String? = null
) {
    /** Whether the search query is blank (no active search). */
    val isQueryBlank: Boolean get() = query.isBlank()

    /** Whether there are any catalog results to display. */
    val hasResults: Boolean get() = songs.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
}
