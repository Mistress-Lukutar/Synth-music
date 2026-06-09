package com.synth.synthmusic.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.synth.synthmusic.ui.ai.AiScreen
import com.synth.synthmusic.ui.library.LibraryScreen
import com.synth.synthmusic.ui.playlists.PlaylistsScreen
import com.synth.synthmusic.ui.search.SearchScreen

/**
 * Root container hosting the primary bottom-navigation tab contents.
 *
 * This composable no longer owns a [Scaffold]; the root [Scaffold] with the
 * MiniPlayer and BottomNav lives in [AppNavigation].
 *
 * @param selectedTab Currently selected bottom-nav tab index.
 * @param onNavigateToPlaylistDetail Navigate to playlist detail.
 * @param onNavigateToSongInfo Navigate to song info.
 * @param onNavigateToEditMetadata Navigate to metadata editor.
 * @param onNavigateToAlbumDetail Navigate to album detail.
 * @param onNavigateToArtistDetail Navigate to artist detail.
 * @param onNavigateToGenreDetail Navigate to genre detail.
 * @param onNavigateToSettings Navigate to settings.
 * @param modifier Modifier for the root container.
 */
@Composable
fun MainScreen(
    selectedTab: Int,
    onNavigateToPlaylistDetail: (Long) -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    onNavigateToAlbumDetail: (String, String) -> Unit,
    onNavigateToArtistDetail: (String) -> Unit,
    onNavigateToGenreDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
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
                onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
                onNavigateToSongInfo = onNavigateToSongInfo,
                onNavigateToEditMetadata = onNavigateToEditMetadata,
                onNavigateToAlbumDetail = onNavigateToAlbumDetail,
                onNavigateToArtistDetail = onNavigateToArtistDetail,
                onNavigateToGenreDetail = onNavigateToGenreDetail,
                onNavigateToSettings = onNavigateToSettings
            )

            1 -> PlaylistsScreen(
                onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.fillMaxSize()
            )

            2 -> AiScreen(
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.fillMaxSize()
            )

            3 -> SearchScreen(
                onNavigateToSongInfo = onNavigateToSongInfo,
                onNavigateToEditMetadata = onNavigateToEditMetadata,
                onNavigateToAlbumDetail = onNavigateToAlbumDetail,
                onNavigateToArtistDetail = onNavigateToArtistDetail,
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
