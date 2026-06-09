package com.synth.synthmusic.ui.search

import com.synth.synthmusic.domain.model.Song

/**
 * UI state for the standalone search screen.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<Song> = emptyList(),
    val currentSongId: String? = null
)
