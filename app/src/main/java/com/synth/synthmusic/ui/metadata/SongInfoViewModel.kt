package com.synth.synthmusic.ui.metadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.CheckWritePermissionUseCase
import com.synth.synthmusic.domain.usecase.UpdateMetadataUseCase
import com.synth.synthmusic.domain.usecase.WriteArtworkToMp3UseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
/**
 * ViewModel for the song info screen — provides read-only display plus in-place editing.
 *
 * Artwork changes are accumulated as pending bytes in [SongInfoUiState.pendingArtworkBytes]
 * and committed together with text metadata when the user saves. Writing to the MP3 file
 * requires [MANAGE_EXTERNAL_STORAGE] on Android 10+; the UI state exposes
 * [SongInfoUiState.hasWritePermission] so the screen can show a rationale banner.
 */
class SongInfoViewModel(
    private val songId: String,
    private val songRepository: SongRepository,
    private val checkWritePermission: CheckWritePermissionUseCase,
    private val writeArtworkUseCase: WriteArtworkToMp3UseCase,
    private val updateMetadataUseCase: UpdateMetadataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SongInfoUiState(hasWritePermission = checkWritePermission())
    )
    val uiState: StateFlow<SongInfoUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableStateFlow<SongInfoUiEvent?>(null)

    /** One-shot UI events; consumed via [consumeEvent]. */
    val uiEvent: StateFlow<SongInfoUiEvent?> = _uiEvent.asStateFlow()

    init {
        songRepository.observeSongById(songId)
            .onEach { result ->
                _uiState.update { it.copy(song = result) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Dispatches a screen event to the ViewModel.
     */
    fun onEvent(event: SongInfoEvent) {
        when (event) {
            is SongInfoEvent.ToggleEditMode -> toggleEditMode()
            is SongInfoEvent.Save -> save(event)
            is SongInfoEvent.ArtworkPicked -> artworkPicked(event)
            is SongInfoEvent.RemoveArtwork -> removeArtwork()
            is SongInfoEvent.ResetArtwork -> resetArtwork()
            is SongInfoEvent.RefreshPermission -> refreshPermission()
        }
    }

    private fun toggleEditMode() {
        _uiState.update { it.copy(isEditing = !it.isEditing) }
    }

    private fun save(event: SongInfoEvent.Save) {
        if (!_uiState.value.hasWritePermission) return
        val current = _uiState.value.song ?: return
        viewModelScope.launch {
            updateMetadataUseCase(
                song = current,
                title = event.title,
                artist = event.artist,
                album = event.album,
                genre = event.genre,
                year = event.year,
                comment = event.comment,
                lyrics = event.lyrics,
                artworkBytes = _uiState.value.pendingArtworkBytes
            )
            .onSuccess {
                _uiState.update { it.copy(isEditing = false, pendingArtworkBytes = null) }
            }
            .onFailure {
                _uiEvent.value = SongInfoUiEvent.Message("Failed to save metadata")
            }
        }
    }

    private fun artworkPicked(event: SongInfoEvent.ArtworkPicked) {
        _uiState.update { it.copy(pendingArtworkBytes = event.bytes) }
    }

    private fun removeArtwork() {
        viewModelScope.launch {
            val current = _uiState.value.song ?: return@launch
            writeArtworkUseCase.removeArtwork(current)
            _uiState.update { it.copy(pendingArtworkBytes = null) }
        }
    }

    private fun resetArtwork() {
        viewModelScope.launch {
            val current = _uiState.value.song ?: return@launch
            writeArtworkUseCase.resetToEmbedded(current)
            _uiState.update { it.copy(pendingArtworkBytes = null) }
        }
    }

    private fun refreshPermission() {
        _uiState.update { it.copy(hasWritePermission = checkWritePermission()) }
    }

    /** Consumes the pending one-shot event. */
    fun consumeEvent() {
        _uiEvent.value = null
    }
}
