package com.synth.synthmusic.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.synth.synthmusic.ui.ai.AiScreen
import com.synth.synthmusic.ui.components.SynthBottomNav
import com.synth.synthmusic.ui.library.LibraryScreen
import com.synth.synthmusic.ui.library.components.MiniPlayer
import com.synth.synthmusic.ui.playlists.PlaylistsScreen
import com.synth.synthmusic.ui.settings.SettingsScreen
import org.koin.androidx.compose.koinViewModel
import com.synth.synthmusic.ui.library.LibraryViewModel

/**
 * Root scaffold hosting the primary bottom-navigation destinations.
 *
 * Displays the MiniPlayer above the bottom nav when a track is active.
 *
 * @param onNavigateToNowPlaying Navigate to full-screen player.
 * @param onNavigateToSearch Navigate to search.
 * @param onNavigateToQueue Navigate to queue.
 * @param onNavigateToPlaylistDetail Navigate to playlist detail.
 * @param onNavigateToSongInfo Navigate to song info.
 * @param onNavigateToEditMetadata Navigate to metadata editor.
 * @param onNavigateToAlbumDetail Navigate to album detail.
 * @param onNavigateToArtistDetail Navigate to artist detail.
 * @param onNavigateToFolderDetail Navigate to folder detail.
 */
@Composable
fun MainScreen(
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToPlaylistDetail: (Long) -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    onNavigateToAlbumDetail: (String, String) -> Unit,
    onNavigateToArtistDetail: (String) -> Unit,
    onNavigateToFolderDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    libraryViewModel: LibraryViewModel = koinViewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val playback by libraryViewModel.currentPlayback.collectAsState()
    val uiState by libraryViewModel.uiState.collectAsState()
    val currentSong = uiState.songs.find { it.id == playback.currentSongId }
        ?: uiState.favoriteSongs.find { it.id == playback.currentSongId }
        ?: uiState.topSongs.find { it.id == playback.currentSongId }
        ?: uiState.recentSongs.find { it.id == playback.currentSongId }
        ?: uiState.historySongs.find { it.id == playback.currentSongId }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Column {
                MiniPlayer(
                    song = currentSong,
                    isPlaying = playback.isPlaying,
                    positionMs = playback.positionMs,
                    durationMs = playback.durationMs,
                    onTogglePlayPause = { libraryViewModel.togglePlayPause() },
                    onNext = { libraryViewModel.skipNext() },
                    onExpand = onNavigateToNowPlaying
                )
                SynthBottomNav(
                    selectedIndex = selectedTab,
                    onItemSelected = { selectedTab = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(bottom = innerPadding.calculateBottomPadding()))
        ) {
            when (selectedTab) {
                0 -> LibraryScreen(
                    onNavigateToNowPlaying = onNavigateToNowPlaying,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToQueue = onNavigateToQueue,
                    onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
                    onNavigateToSongInfo = onNavigateToSongInfo,
                    onNavigateToEditMetadata = onNavigateToEditMetadata,
                    onNavigateToAlbumDetail = onNavigateToAlbumDetail,
                    onNavigateToArtistDetail = onNavigateToArtistDetail,
                    onNavigateToFolderDetail = onNavigateToFolderDetail
                )

                1 -> PlaylistsScreen(
                    onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
                    modifier = Modifier.fillMaxSize()
                )

                2 -> AiScreen(modifier = Modifier.fillMaxSize())
                3 -> SettingsScreen(
                    showBackButton = false,
                    onNavigateBack = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
