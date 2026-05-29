package com.synth.synthmusic.ui.library

import com.synth.synthmusic.domain.model.Album
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import com.synth.synthmusic.domain.model.Song

/**
 * UI state for the library screen.
 */
data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.Songs,
    val songs: List<Song> = emptyList(),
    val queueSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val isScanning: Boolean = false,
    val scanError: String? = null,
    val favoriteSongs: List<Song> = emptyList(),
    val topSongs: List<Song> = emptyList(),
    val recentSongs: List<Song> = emptyList(),
    val recentCollections: List<RecentlyPlayedCollection> = emptyList(),
    val historySongs: List<Song> = emptyList(),
    val folders: List<String> = emptyList(),
    val songCount: Int = 0
)

enum class LibraryTab {
    Songs, Albums, Artists, Folders, Playlists, Favorites, Top, Recent, History
}
