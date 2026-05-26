package com.synth.synthmusic.ui.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Full-screen player with artwork, waveform seekbar, and playback controls.
 */
@Composable
fun NowPlayingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Now Playing")
    }
}
