package com.synth.synthmusic.data.media

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages smooth volume fade in/out for an ExoPlayer instance.
 *
 * @param player ExoPlayer to control.
 */
class AudioFadeManager(
    private val player: ExoPlayer
) {
    private var fadeJob: Job? = null

    /**
     * Gradually change player volume to [targetVolume] over [durationMs].
     */
    fun fadeTo(targetVolume: Float, durationMs: Long = 500) {
        fadeJob?.cancel()
        fadeJob = CoroutineScope(Dispatchers.Main).launch {
            val startVolume = player.volume
            val steps = 20
            val stepDuration = durationMs / steps
            for (i in 1..steps) {
                if (!isActive) return@launch
                val progress = i / steps.toFloat()
                player.volume = startVolume + (targetVolume - startVolume) * progress
                delay(stepDuration.toLong())
            }
            player.volume = targetVolume
        }
    }

    fun cancel() {
        fadeJob?.cancel()
    }
}
