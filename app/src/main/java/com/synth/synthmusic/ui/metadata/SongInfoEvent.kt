package com.synth.synthmusic.ui.metadata

/** Events for the song info screen. */
sealed class SongInfoEvent {
    /** Toggles between read-only view and edit mode. */
    object ToggleEditMode : SongInfoEvent()
    /** Saves all text fields plus any pending artwork. */
    data class Save(
        val title: String,
        val artist: String,
        val album: String,
        val genre: String,
        val year: String,
        val comment: String,
        val lyrics: String
    ) : SongInfoEvent()
    /** Stores artwork bytes picked from the gallery (null clears the pending pick). */
    data class ArtworkPicked(val bytes: ByteArray?) : SongInfoEvent()
    /** Deletes the artwork from the MP3 file immediately. */
    object RemoveArtwork : SongInfoEvent()
    /** Re-extracts the embedded artwork from the MP3 file immediately. */
    object ResetArtwork : SongInfoEvent()
    /** Re-checks the storage write permission (e.g. after returning from system settings). */
    object RefreshPermission : SongInfoEvent()
}
