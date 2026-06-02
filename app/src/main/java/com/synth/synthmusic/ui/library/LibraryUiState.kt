package com.synth.synthmusic.ui.library

import com.synth.synthmusic.domain.model.Album
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import com.synth.synthmusic.domain.model.Song

/**
 * UI state for the library screen.
 */
data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.Queue,
    val queueSongs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val isScanning: Boolean = false,
    val scanError: String? = null,
    val topSongs: List<Song> = emptyList(),
    val recentCollections: List<RecentlyPlayedCollection> = emptyList(),
    val historySongs: List<Song> = emptyList()
)

enum class LibraryTab {
    Queue, Artists, Top, History
}
