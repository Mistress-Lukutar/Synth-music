package com.synth.synthmusic.ui.library

import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection

/**
 * UI state for the library screen.
 */
data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.Home,
    val artists: List<Artist> = emptyList(),
    val genres: List<String> = emptyList(),
    val recentCollections: List<RecentlyPlayedCollection> = emptyList()
)

enum class LibraryTab {
    Home, Artists, Genres
}
