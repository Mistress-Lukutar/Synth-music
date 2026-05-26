package com.synth.synthmusic.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.synth.synthmusic.ui.library.LibraryScreen
import com.synth.synthmusic.ui.nowplaying.NowPlayingScreen
import com.synth.synthmusic.ui.queue.QueueScreen
import com.synth.synthmusic.ui.search.SearchScreen
import com.synth.synthmusic.ui.settings.SettingsScreen

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
                onNavigateToQueue = { navController.navigate(QueueRoute) }
            )
        }
        composable<NowPlayingRoute> {
            NowPlayingScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToQueue = { navController.navigate(QueueRoute) },
                onNavigateToEqualizer = { navController.navigate(EqualizerRoute) }
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
    }
}
