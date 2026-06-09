package com.synth.synthmusic.ui.search

/**
 * Events emitted by the search UI to the ViewModel.
 */
sealed class SearchEvent {
    data class QueryChanged(val query: String) : SearchEvent()
    data class PlaySong(val songId: String) : SearchEvent()
}
