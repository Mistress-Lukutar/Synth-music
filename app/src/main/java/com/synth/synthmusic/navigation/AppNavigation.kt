package com.synth.synthmusic.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.synth.synthmusic.ui.albums.AlbumDetailScreen
import com.synth.synthmusic.ui.artists.ArtistDetailScreen
import com.synth.synthmusic.ui.components.SynthBottomNav
import com.synth.synthmusic.ui.home.MainScreen
import com.synth.synthmusic.ui.library.components.MiniPlayer
import com.synth.synthmusic.ui.metadata.EditMetadataScreen
import com.synth.synthmusic.ui.metadata.SongInfoScreen
import com.synth.synthmusic.ui.nowplaying.NowPlayingScreen
import com.synth.synthmusic.ui.playback.PlaybackViewModel
import com.synth.synthmusic.ui.playlists.PlaylistDetailScreen
import com.synth.synthmusic.ui.genres.GenreDetailScreen
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

    val playback by playbackViewModel.playbackState.collectAsStateWithLifecycle()
    val currentPositionMs by playbackViewModel.currentPositionMs.collectAsStateWithLifecycle()
    val currentDurationMs by playbackViewModel.currentDurationMs.collectAsStateWithLifecycle()
    val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()

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
                        positionMs = currentPositionMs,
                        durationMs = currentDurationMs,
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
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { popEnterTransition() },
            popExitTransition = { popExitTransition() }
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
                    onNavigateToPlaylistDetail = { navController.navigate(PlaylistDetailRoute(it)) },
                    onNavigateToSongInfo = { navController.navigate(SongInfoRoute(it)) },
                    onNavigateToEditMetadata = { navController.navigate(EditMetadataRoute(it)) },
                    onNavigateToAlbumDetail = { title, artist ->
                        navController.navigate(AlbumDetailRoute(title, artist))
                    },
                    onNavigateToArtistDetail = { name ->
                        navController.navigate(ArtistDetailRoute(name))
                    },
                    onNavigateToGenreDetail = { genre ->
                        navController.navigate(GenreDetailRoute(genre))
                    },
                    onNavigateToSettings = { navController.navigate(SettingsRoute) }
                )
            }

            composable<NowPlayingRoute> {
                NowPlayingScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToVisualizer = { navController.navigate(VisualizerRoute) }
                )
            }
            composable<GenreDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<GenreDetailRoute>()
                GenreDetailScreen(
                    genre = route.genre,
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

// --- Transition Constants (milliseconds) ---
private const val PUSH_DURATION = 250
private const val POP_DURATION = 200
private const val MODAL_DURATION = 300
private const val SPLASH_FADE_DURATION = 400

/**
 * Default enter transition for push navigation.
 *
 * - Modal screens ([NowPlayingRoute], [VisualizerRoute]) slide up from the bottom.
 * - After [SplashRoute] performs a simple fade.
 * - Everything else slides in horizontally from the right with a fade.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterTransition() = when {
    targetState.destination.hasRoute<NowPlayingRoute>()
            || targetState.destination.hasRoute<VisualizerRoute>() ->
        slideInVertically(
            animationSpec = tween(MODAL_DURATION, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ) + fadeIn(tween(MODAL_DURATION, easing = FastOutSlowInEasing))

    initialState.destination.hasRoute<SplashRoute>() ->
        fadeIn(tween(SPLASH_FADE_DURATION, easing = LinearOutSlowInEasing))

    else ->
        slideInHorizontally(
            animationSpec = tween(PUSH_DURATION, easing = FastOutSlowInEasing),
            initialOffsetX = { it }
        ) + fadeIn(tween(PUSH_DURATION, easing = FastOutSlowInEasing))
}

/**
 * Default exit transition for push navigation.
 *
 * - When the target is modal, the current screen fades out.
 * - When leaving [SplashRoute], fades out.
 * - Otherwise the current screen slides out slightly to the left while fading.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitTransition() = when {
    targetState.destination.hasRoute<NowPlayingRoute>()
            || targetState.destination.hasRoute<VisualizerRoute>() ->
        fadeOut(tween(MODAL_DURATION, easing = FastOutSlowInEasing))

    targetState.destination.hasRoute<SplashRoute>() ->
        fadeOut(tween(SPLASH_FADE_DURATION, easing = LinearOutSlowInEasing))

    else ->
        slideOutHorizontally(
            animationSpec = tween(PUSH_DURATION, easing = FastOutSlowInEasing),
            targetOffsetX = { -it / 3 }
        ) + fadeOut(tween(PUSH_DURATION, easing = FastOutSlowInEasing))
}

/**
 * Enter transition when popping back from the back-stack.
 *
 * - Returning from a modal screen performs a simple fade.
 * - Otherwise the previous screen slides in slightly from the left with a fade.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterTransition() = when {
    initialState.destination.hasRoute<NowPlayingRoute>()
            || initialState.destination.hasRoute<VisualizerRoute>() ->
        fadeIn(tween(POP_DURATION, easing = FastOutSlowInEasing))

    else ->
        slideInHorizontally(
            animationSpec = tween(POP_DURATION, easing = FastOutSlowInEasing),
            initialOffsetX = { -it / 3 }
        ) + fadeIn(tween(POP_DURATION, easing = FastOutSlowInEasing))
}

/**
 * Exit transition when popping back from the back-stack.
 *
 * - Modal screens slide down while fading.
 * - Everything else slides out horizontally to the right while fading.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitTransition() = when {
    initialState.destination.hasRoute<NowPlayingRoute>()
            || initialState.destination.hasRoute<VisualizerRoute>() ->
        slideOutVertically(
            animationSpec = tween(POP_DURATION, easing = FastOutSlowInEasing),
            targetOffsetY = { it }
        ) + fadeOut(tween(POP_DURATION, easing = FastOutSlowInEasing))

    else ->
        slideOutHorizontally(
            animationSpec = tween(POP_DURATION, easing = FastOutSlowInEasing),
            targetOffsetX = { it }
        ) + fadeOut(tween(POP_DURATION, easing = FastOutSlowInEasing))
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
