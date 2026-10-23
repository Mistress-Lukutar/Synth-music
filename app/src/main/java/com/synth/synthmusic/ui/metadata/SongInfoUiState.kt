package com.synth.synthmusic.ui.metadata

import com.synth.synthmusic.domain.model.Song

/**
 * UI state for the song info screen (read-only view plus in-place editing).
 *
 * @property song the song being displayed, null until first load.
 * @property hasWritePermission whether the app may write MP3 metadata and artwork.
 * @property isEditing whether the screen is currently in edit mode.
 * @property pendingArtworkBytes artwork picked from the gallery, written on save.
 */
data class SongInfoUiState(
    val song: Song? = null,
    val hasWritePermission: Boolean = false,
    val isEditing: Boolean = false,
    val pendingArtworkBytes: ByteArray? = null
)
