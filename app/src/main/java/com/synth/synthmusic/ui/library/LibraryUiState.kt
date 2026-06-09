package com.synth.synthmusic.ui.library

import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import com.synth.synthmusic.domain.model.Song

/**
 * UI state for the library screen.
 */
data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.Home,
    val artists: List<Artist> = emptyList(),
    val genres: List<String> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Song> = emptyList(),
    val isScanning: Boolean = false,
    val scanError: String? = null,
    val recentCollections: List<RecentlyPlayedCollection> = emptyList()
)

enum class LibraryTab {
    Home, Artists, Genres, Search
}
