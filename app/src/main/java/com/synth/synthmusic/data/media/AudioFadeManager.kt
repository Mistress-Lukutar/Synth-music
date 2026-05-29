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
 * @param scope CoroutineScope used for fade animations (operations are dispatched to Main thread).
 */
class AudioFadeManager(
    private val player: ExoPlayer,
    private val scope: CoroutineScope
) {
    private var fadeJob: Job? = null
    private var lastFadeEndTime: Long = 0
    private var lastFadeWasIn: Boolean = false

    /**
     * Whether a fade animation is currently running.
     */
    val isFading: Boolean get() = fadeJob?.isActive == true

    /**
     * Gradually decrease player volume to 0 over [durationMs].
     *
     * @param durationMs fade duration in milliseconds.
     * @param onComplete optional callback invoked after fade completes.
     */
    fun fadeOut(durationMs: Long = 500, onComplete: (() -> Unit)? = null) {
        fadeTo(0f, durationMs, onComplete)
    }

    /**
     * Gradually increase player volume to [targetVolume] over [durationMs].
     *
     * @param durationMs fade duration in milliseconds.
     * @param targetVolume final volume level (e.g. ReplayGain-adjusted).
     */
    fun fadeIn(durationMs: Long = 500, targetVolume: Float = 1f) {
        // Debounce: ignore fade-in requests that arrive < 150 ms after a previous fade-in
        if (lastFadeWasIn && (System.currentTimeMillis() - lastFadeEndTime) < 150) {
            return
        }
        fadeTo(targetVolume, durationMs)
    }

    /**
     * Cancel any running fade animation.
     */
    fun cancel() {
        fadeJob?.cancel()
    }

    private fun fadeTo(
        targetVolume: Float,
        durationMs: Long = 500,
        onComplete: (() -> Unit)? = null
    ) {
        fadeJob?.cancel()
        if (durationMs <= 0) {
            player.volume = targetVolume
            onComplete?.invoke()
            recordFadeEnd(targetVolume > 0f)
            return
        }

        fadeJob = scope.launch(Dispatchers.Main) {
            val startVolume = player.volume
            val steps = 30
            val stepDuration = durationMs / steps
            for (i in 1..steps) {
                if (!isActive) return@launch
                val progress = i / steps.toFloat()
                val eased = smoothstep(progress)
                player.volume = startVolume + (targetVolume - startVolume) * eased
                delay(stepDuration.coerceAtLeast(1))
            }
            player.volume = targetVolume
            onComplete?.invoke()
            recordFadeEnd(targetVolume > 0f)
        }
    }

    private fun recordFadeEnd(wasFadeIn: Boolean) {
        lastFadeEndTime = System.currentTimeMillis()
        lastFadeWasIn = wasFadeIn
    }

    /**
     * Smoothstep easing function for natural-sounding volume changes.
     */
    private fun smoothstep(t: Float): Float = t * t * (3 - 2 * t)
}
