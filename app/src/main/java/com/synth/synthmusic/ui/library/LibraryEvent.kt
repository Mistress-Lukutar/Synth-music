package com.synth.synthmusic.ui.library

/**
 * Events emitted by the library UI to the ViewModel.
 */
sealed class LibraryEvent {
    data class SelectTab(val tab: LibraryTab) : LibraryEvent()
    data object ScanLibrary : LibraryEvent()
    data class PlaySong(val songId: String) : LibraryEvent()
}
