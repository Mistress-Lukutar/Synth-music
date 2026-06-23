package com.synth.synthmusic.domain.model

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class AccentColor { YELLOW, GREEN, BLUE, RED, PURPLE, ORANGE }

/**
 * Domain model representing user application settings.
 *
 * @param theme UI theme mode.
 * @param accentColor Accent color for the app.
 * @param autoRescan Whether to automatically rescan library on startup.
 * @param playbackSpeed Current playback speed.
 * @param playbackPitch Current playback pitch.
 * @param skipSilence Whether to skip silent parts during playback.
 */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val accentColor: AccentColor = AccentColor.YELLOW,
    val autoRescan: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val skipSilence: Boolean = false
)
