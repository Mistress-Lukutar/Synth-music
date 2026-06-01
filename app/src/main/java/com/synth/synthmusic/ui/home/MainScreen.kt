package com.synth.synthmusic.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.synth.synthmusic.ui.ai.AiScreen
import com.synth.synthmusic.ui.library.LibraryScreen
import com.synth.synthmusic.ui.playlists.PlaylistsScreen
import com.synth.synthmusic.ui.settings.SettingsScreen

/**
 * Root container hosting the primary bottom-navigation tab contents.
 *
 * This composable no longer owns a [Scaffold]; the root [Scaffold] with the
 * MiniPlayer and BottomNav lives in [AppNavigation].
 *
 * @param selectedTab Currently selected bottom-nav tab index.
 * @param onNavigateToNowPlaying Navigate to full-screen player.
 * @param onNavigateToSearch Navigate to search.
 * @param onNavigateToQueue Navigate to queue.
 * @param onNavigateToPlaylistDetail Navigate to playlist detail.
 * @param onNavigateToSongInfo Navigate to song info.
 * @param onNavigateToEditMetadata Navigate to metadata editor.
 * @param onNavigateToAlbumDetail Navigate to album detail.
 * @param onNavigateToArtistDetail Navigate to artist detail.
 * @param modifier Modifier for the root container.
 */
@Composable
fun MainScreen(
    selectedTab: Int,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToPlaylistDetail: (Long) -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    onNavigateToAlbumDetail: (String, String) -> Unit,
    onNavigateToArtistDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = selectedTab,
        animationSpec = tween(durationMillis = 150),
        modifier = modifier.fillMaxSize(),
        label = "MainScreenTabCrossfade"
    ) { tab ->
        when (tab) {
            0 -> LibraryScreen(
                onNavigateToNowPlaying = onNavigateToNowPlaying,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToQueue = onNavigateToQueue,
                onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
                onNavigateToSongInfo = onNavigateToSongInfo,
                onNavigateToEditMetadata = onNavigateToEditMetadata,
                onNavigateToAlbumDetail = onNavigateToAlbumDetail,
                onNavigateToArtistDetail = onNavigateToArtistDetail
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
