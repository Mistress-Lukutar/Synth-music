package com.synth.synthmusic.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.synth.synthmusic.ui.albums.AlbumDetailScreen
import com.synth.synthmusic.ui.artists.ArtistDetailScreen
import com.synth.synthmusic.ui.bookmarks.BookmarksScreen
import com.synth.synthmusic.ui.components.SynthBottomNav
import com.synth.synthmusic.ui.equalizer.EqualizerScreen
import com.synth.synthmusic.ui.home.MainScreen
import com.synth.synthmusic.ui.library.LibraryViewModel
import com.synth.synthmusic.ui.library.components.MiniPlayer
import com.synth.synthmusic.ui.metadata.BatchEditScreen
import com.synth.synthmusic.ui.metadata.EditMetadataScreen
import com.synth.synthmusic.ui.metadata.SongInfoScreen
import com.synth.synthmusic.ui.nowplaying.NowPlayingScreen
import com.synth.synthmusic.ui.playback.PlaybackViewModel
import com.synth.synthmusic.ui.playlists.PlaylistDetailScreen
import com.synth.synthmusic.ui.queue.QueueScreen
import com.synth.synthmusic.ui.search.SearchScreen
import com.synth.synthmusic.ui.settings.SettingsScreen
import com.synth.synthmusic.ui.splash.SplashScreen
import com.synth.synthmusic.ui.visualizer.VisualizerScreen
import org.koin.androidx.compose.koinViewModel

/**
 * Root navigation host defining all application routes.
 *
 * Wraps the [NavHost] in a root [Scaffold] that conditionally shows the
 * [MiniPlayer] and [SynthBottomNav] for every screen except [SplashRoute]
 * and [NowPlayingRoute].
 *
 * @param navController Navigation controller.
 * @param modifier Modifier for the NavHost.
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val playbackViewModel: PlaybackViewModel = koinViewModel()
    val libraryViewModel: LibraryViewModel = koinViewModel()

    val playback by playbackViewModel.playbackState.collectAsState()
    val libraryUiState by libraryViewModel.uiState.collectAsState()
    val currentSong = libraryUiState.songs.find { it.id == playback.currentSongId }
        ?: libraryUiState.topSongs.find { it.id == playback.currentSongId }
        ?: libraryUiState.historySongs.find { it.id == playback.currentSongId }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val showBottomBar = !currentDestination.hasRoute<SplashRoute>()
            && !currentDestination.hasRoute<NowPlayingRoute>()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                Column {
                    MiniPlayer(
                        song = currentSong,
                        isPlaying = playback.isPlaying,
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        onTogglePlayPause = { playbackViewModel.playPause() },
                        onPrevious = { playbackViewModel.previous() },
                        onNext = { playbackViewModel.next() },
                        onExpand = { navController.navigate(NowPlayingRoute) }
                    )
                    SynthBottomNav(
                        selectedIndex = selectedTab,
                        onItemSelected = { index ->
                            selectedTab = index
                            if (!currentDestination.hasRoute<HomeRoute>()) {
                                navController.popBackStack(HomeRoute, inclusive = false)
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SplashRoute,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable<SplashRoute> {
                SplashScreen(
                    onNavigateToHome = {
                        navController.navigate(HomeRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    }
                )
            }

            composable<HomeRoute> {
                MainScreen(
                    selectedTab = selectedTab,
                    onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) },
                    onNavigateToSearch = { navController.navigate(SearchRoute) },
                    onNavigateToQueue = { navController.navigate(QueueRoute) },
                    onNavigateToPlaylistDetail = { navController.navigate(PlaylistDetailRoute(it)) },
                    onNavigateToSongInfo = { navController.navigate(SongInfoRoute(it)) },
                    onNavigateToEditMetadata = { navController.navigate(EditMetadataRoute(it)) },
                    onNavigateToAlbumDetail = { title, artist ->
                        navController.navigate(AlbumDetailRoute(title, artist))
                    },
                    onNavigateToArtistDetail = { name ->
                        navController.navigate(ArtistDetailRoute(name))
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
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) },
                    onNavigateToSongInfo = { navController.navigate(SongInfoRoute(it)) },
                    onNavigateToEditMetadata = { navController.navigate(EditMetadataRoute(it)) }
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
            composable<AlbumDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<AlbumDetailRoute>()
                AlbumDetailScreen(
                    albumTitle = route.albumTitle,
                    albumArtist = route.albumArtist,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) },
                    onNavigateToSongInfo = { navController.navigate(SongInfoRoute(it)) },
                    onNavigateToEditMetadata = { navController.navigate(EditMetadataRoute(it)) }
                )
            }
            composable<ArtistDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ArtistDetailRoute>()
                ArtistDetailScreen(
                    artistName = route.artistName,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) },
                    onNavigateToSongInfo = { navController.navigate(SongInfoRoute(it)) },
                    onNavigateToEditMetadata = { navController.navigate(EditMetadataRoute(it)) },
                    onNavigateToAlbumDetail = { title, artist ->
                        navController.navigate(AlbumDetailRoute(title, artist))
                    }
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
            composable<BatchEditRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<BatchEditRoute>()
                BatchEditScreen(
                    songIds = route.songIds,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<VisualizerRoute> {
                VisualizerScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<PlaylistDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<PlaylistDetailRoute>()
                PlaylistDetailScreen(
                    playlistId = route.playlistId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) },
                    onNavigateToSongInfo = { navController.navigate(SongInfoRoute(it)) },
                    onNavigateToEditMetadata = { navController.navigate(EditMetadataRoute(it)) }
                )
            }
        }
    }
}

/**
 * Checks whether this [NavDestination] matches the given type-safe [T] route.
 *
 * Works around older Navigation-Compose versions where [NavDestination.hasRoute]
 * does not accept reified type parameters.
 */
private inline fun <reified T> NavDestination?.hasRoute(): Boolean {
    return this?.route == T::class.qualifiedName
}
