package com.synth.synthmusic.ui.search

/**
 * Events emitted by the search UI to the ViewModel.
 */
sealed class SearchEvent {
    /** The user typed or modified the search query. */
    data class QueryChanged(val query: String) : SearchEvent()

    /** The user tapped a song to play it. */
    data class PlaySong(val songId: String) : SearchEvent()

    /** The user tapped a recent search entry from history. */
    data class HistorySelected(val query: String) : SearchEvent()

    /** The user requested removal of a specific history entry. */
    data class RemoveHistoryEntry(val query: String) : SearchEvent()

    /** The user requested clearing all search history. */
    object ClearHistory : SearchEvent()
}
