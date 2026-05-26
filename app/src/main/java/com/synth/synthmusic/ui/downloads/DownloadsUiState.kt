package com.synth.synthmusic.ui.downloads

import com.synth.synthmusic.domain.model.Song

/**
 * UI state for the downloads manager screen.
 *
 * @param tracks the list of locally available tracks.
 * @param isLoading true while data is being loaded.
 */
data class DownloadsUiState(
    val tracks: List<Song> = emptyList(),
    val isLoading: Boolean = false
)
