package com.synth.synthmusic.ui.metadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.CheckWritePermissionUseCase
import com.synth.synthmusic.domain.usecase.UpdateMetadataUseCase
import com.synth.synthmusic.domain.usecase.WriteArtworkToMp3UseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for editing song metadata and artwork.
 *
 * Artwork changes are accumulated as pending bytes and committed together with text metadata
 * when the user presses Save. Writing to the MP3 file requires [MANAGE_EXTERNAL_STORAGE]
 * on Android 10+; the ViewModel exposes [hasWritePermission] so the UI can show a rationale
 * banner when the permission is missing.
 */
class EditMetadataViewModel(
    private val songId: String,
    private val songRepository: SongRepository,
    private val checkWritePermission: CheckWritePermissionUseCase,
    private val writeArtworkUseCase: WriteArtworkToMp3UseCase,
    private val updateMetadataUseCase: UpdateMetadataUseCase
) : ViewModel() {

    private val _song = MutableStateFlow<Song?>(null)
    val song: StateFlow<Song?> = _song.asStateFlow()

    private val _hasWritePermission = MutableStateFlow(checkWritePermission())
    val hasWritePermission: StateFlow<Boolean> = _hasWritePermission.asStateFlow()

    private val _pendingArtworkBytes = MutableStateFlow<ByteArray?>(null)
    val pendingArtworkBytes: StateFlow<ByteArray?> = _pendingArtworkBytes.asStateFlow()

    init {
        songRepository.observeSongById(songId)
            .onEach { _song.value = it }
            .launchIn(viewModelScope)
    }

    /**
     * Re-checks the storage write permission (call after returning from system settings).
     */
    fun refreshPermission() {
        _hasWritePermission.value = checkWritePermission()
    }

    /**
     * Called when the user picks a new image from the gallery.
     */
    fun onArtworkPicked(bytes: ByteArray?) {
        _pendingArtworkBytes.value = bytes
    }

    /**
     * Removes the artwork from the MP3 file and clears the local cache.
     */
    fun removeArtwork() {
        viewModelScope.launch {
            val current = _song.value ?: return@launch
            writeArtworkUseCase.removeArtwork(current)
            _pendingArtworkBytes.value = null
        }
    }

    /**
     * Re-extracts the embedded artwork from the MP3 file.
     */
    fun resetArtwork() {
        viewModelScope.launch {
            val current = _song.value ?: return@launch
            writeArtworkUseCase.resetToEmbedded(current)
            _pendingArtworkBytes.value = null
        }
    }

    /**
     * Saves text metadata and any pending artwork bytes to the MP3 file and database.
     */
    fun save(
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: String,
        comment: String,
        lyrics: String? = null
    ) {
        if (!_hasWritePermission.value) return
        val current = _song.value ?: return
        viewModelScope.launch {
            updateMetadataUseCase(
                song = current,
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                year = year,
                comment = comment,
                lyrics = lyrics,
                artworkBytes = _pendingArtworkBytes.value
            )
            _pendingArtworkBytes.value = null
        }
    }
}
