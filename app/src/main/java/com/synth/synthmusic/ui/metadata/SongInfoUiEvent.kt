package com.synth.synthmusic.ui.metadata

/** One-shot events emitted by [SongInfoViewModel] to the screen. */
sealed class SongInfoUiEvent {
    /** Shows a transient snackbar with [text]. */
    data class Message(val text: String) : SongInfoUiEvent()
}
