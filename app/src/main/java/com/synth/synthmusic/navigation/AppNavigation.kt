package com.synth.synthmusic.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.synth.synthmusic.ui.library.LibraryScreen
import com.synth.synthmusic.ui.nowplaying.NowPlayingScreen
import com.synth.synthmusic.ui.bookmarks.BookmarksScreen
import com.synth.synthmusic.ui.downloads.DownloadsScreen
import com.synth.synthmusic.ui.equalizer.EqualizerScreen
import com.synth.synthmusic.ui.folders.FolderDetailScreen
import com.synth.synthmusic.ui.folders.FoldersScreen
import com.synth.synthmusic.ui.metadata.EditMetadataScreen
import com.synth.synthmusic.ui.metadata.SongInfoScreen
import com.synth.synthmusic.ui.playlists.PlaylistDetailScreen
import com.synth.synthmusic.ui.playlists.PlaylistsScreen
import com.synth.synthmusic.ui.queue.QueueScreen
import com.synth.synthmusic.ui.search.SearchScreen
import com.synth.synthmusic.ui.settings.SettingsScreen
import com.synth.synthmusic.ui.albums.AlbumDetailScreen
import com.synth.synthmusic.ui.artists.ArtistDetailScreen
import com.synth.synthmusic.ui.visualizer.VisualizerScreen

/**
 * Root navigation host defining all application routes.
 *
 * @param navController Navigation controller.
 * @param modifier Modifier for the NavHost.
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = LibraryRoute,
        modifier = modifier
    ) {
        composable<LibraryRoute> {
            LibraryScreen(
                onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) },
                onNavigateToSearch = { navController.navigate(SearchRoute) },
                onNavigateToSettings = { navController.navigate(SettingsRoute) },
                onNavigateToQueue = { navController.navigate(QueueRoute) },
                onNavigateToPlaylistDetail = { navController.navigate(PlaylistDetailRoute(it)) },
                onNavigateToSongInfo = { navController.navigate(SongInfoRoute(it)) },
                onNavigateToEditMetadata = { navController.navigate(EditMetadataRoute(it)) },
                onNavigateToAlbumDetail = { title, artist ->
                    navController.navigate(AlbumDetailRoute(title, artist))
                },
                onNavigateToArtistDetail = { name ->
                    navController.navigate(ArtistDetailRoute(name))
                },
                onNavigateToFolderDetail = { path ->
                    navController.navigate(FolderDetailRoute(path))
                }
            )
        }
        composable<NowPlayingRoute> {
            NowPlayingScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToQueue = { navController.navigate(QueueRoute) },
                onNavigateToEqualizer = { navController.navigate(EqualizerRoute) },
                onNavigateToVisualizer = { navController.navigate(VisualizerRoute) }
            )
        }
        composable<QueueRoute> {
            QueueScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<EqualizerRoute> {
            EqualizerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<BookmarksRoute> {
            BookmarksScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<FolderDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FolderDetailRoute>()
            FolderDetailScreen(
                folderPath = route.folderPath,
                onNavigateBack = { navController.popBackStack() },
                onSongClick = { navController.navigate(NowPlayingRoute) }
            )
        }
        composable<AlbumDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AlbumDetailRoute>()
            AlbumDetailScreen(
                albumTitle = route.albumTitle,
                albumArtist = route.albumArtist,
                onNavigateBack = { navController.popBackStack() },
                onSongClick = { navController.navigate(NowPlayingRoute) }
            )
        }
        composable<ArtistDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ArtistDetailRoute>()
            ArtistDetailScreen(
                artistName = route.artistName,
                onNavigateBack = { navController.popBackStack() },
                onSongClick = { navController.navigate(NowPlayingRoute) }
            )
        }
        composable<SongInfoRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SongInfoRoute>()
            SongInfoScreen(
                songId = route.songId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<EditMetadataRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<EditMetadataRoute>()
            EditMetadataScreen(
                songId = route.songId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<VisualizerRoute> {
            VisualizerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<DownloadsRoute> {
            DownloadsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<PlaylistDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PlaylistDetailRoute>()
            PlaylistDetailScreen(
                playlistId = route.playlistId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
