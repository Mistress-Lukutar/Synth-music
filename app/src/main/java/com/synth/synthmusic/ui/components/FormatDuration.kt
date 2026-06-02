package com.synth.synthmusic.ui.components

import java.util.Locale

/**
 * Formats a duration in milliseconds to "M:SS".
 */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
