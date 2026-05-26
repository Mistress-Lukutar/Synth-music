package com.synth.synthmusic.ui.metadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.BatchUpdateMetadataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for batch editing metadata of multiple songs.
 *
 * @param songIds the list of song identifiers to edit.
 * @param songRepository the repository for fetching song data.
 * @param batchUpdateMetadataUseCase the use case for applying batch updates.
 */
class BatchEditViewModel(
    private val songIds: List<String>,
    private val songRepository: SongRepository,
    private val batchUpdateMetadataUseCase: BatchUpdateMetadataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchEditUiState())
    val uiState: StateFlow<BatchEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val songs = songRepository.getSongsByIds(songIds)
            _uiState.update { it.copy(songs = songs) }
        }
    }

    /**
     * Processes user events.
     */
    fun onEvent(event: BatchEditEvent) {
        when (event) {
            is BatchEditEvent.ToggleField -> toggleField(event.field, event.enabled)
            is BatchEditEvent.UpdateValue -> updateValue(event.field, event.value)
            BatchEditEvent.Save -> save()
        }
    }

    private fun toggleField(field: MetadataField, enabled: Boolean) {
        _uiState.update { state ->
            when (field) {
                MetadataField.TITLE -> state.copy(title = state.title.copy(enabled = enabled))
                MetadataField.ARTIST -> state.copy(artist = state.artist.copy(enabled = enabled))
                MetadataField.ALBUM -> state.copy(album = state.album.copy(enabled = enabled))
                MetadataField.ALBUM_ARTIST -> state.copy(albumArtist = state.albumArtist.copy(enabled = enabled))
                MetadataField.GENRE -> state.copy(genre = state.genre.copy(enabled = enabled))
                MetadataField.YEAR -> state.copy(year = state.year.copy(enabled = enabled))
                MetadataField.TRACK_NUMBER -> state.copy(trackNumber = state.trackNumber.copy(enabled = enabled))
                MetadataField.COMMENT -> state.copy(comment = state.comment.copy(enabled = enabled))
            }
        }
    }

    private fun updateValue(field: MetadataField, value: String) {
        _uiState.update { state ->
            when (field) {
                MetadataField.TITLE -> state.copy(title = state.title.copy(value = value))
                MetadataField.ARTIST -> state.copy(artist = state.artist.copy(value = value))
                MetadataField.ALBUM -> state.copy(album = state.album.copy(value = value))
                MetadataField.ALBUM_ARTIST -> state.copy(albumArtist = state.albumArtist.copy(value = value))
                MetadataField.GENRE -> state.copy(genre = state.genre.copy(value = value))
                MetadataField.YEAR -> state.copy(year = state.year.copy(value = value))
                MetadataField.TRACK_NUMBER -> state.copy(trackNumber = state.trackNumber.copy(value = value))
                MetadataField.COMMENT -> state.copy(comment = state.comment.copy(value = value))
            }
        }
    }

    private fun save() {
        val state = _uiState.value
        if (state.songs.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            batchUpdateMetadataUseCase(
                songs = state.songs,
                title = state.title,
                artist = state.artist,
                album = state.album,
                albumArtist = state.albumArtist,
                genre = state.genre,
                year = state.year,
                trackNumber = state.trackNumber,
                comment = state.comment
            )
            _uiState.update { it.copy(isSaving = false) }
        }
    }
}
