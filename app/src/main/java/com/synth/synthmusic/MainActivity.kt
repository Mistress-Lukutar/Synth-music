package com.synth.synthmusic

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.synth.synthmusic.data.local.datastore.SettingsDataStore
import com.synth.synthmusic.domain.model.ThemeMode
import com.synth.synthmusic.navigation.AppNavigation
import com.synth.synthmusic.ui.theme.SynthMusicTheme
import org.koin.android.ext.android.inject

/**
 * Single entry point for the application.
 */
class MainActivity : ComponentActivity() {

    private val settingsDataStore: SettingsDataStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.startForegroundService(
            this,
            Intent(this, com.synth.synthmusic.service.PlaybackService::class.java)
        )
        enableEdgeToEdge()
        setContent {
            val settings by settingsDataStore.settings.collectAsState(initial = null)
            val darkTheme = when (settings?.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM, null -> null // follow system
            }
            SynthMusicTheme(
                darkTheme = darkTheme,
                accentColor = settings?.accentColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicApp()
                }
            }
        }
    }
}

@Composable
private fun MusicApp() {
    val navController = rememberNavController()
    AppNavigation(navController = navController)
}
