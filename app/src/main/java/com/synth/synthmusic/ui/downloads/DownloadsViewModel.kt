package com.synth.synthmusic.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * ViewModel for the downloads manager screen.
 *
 * Because the app is offline-first, all indexed songs are considered
 * locally downloaded. This ViewModel exposes them as download entries.
 *
 * @param songRepository the repository providing song data.
 */
class DownloadsViewModel(
    private val songRepository: SongRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState(isLoading = true))
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        songRepository.observeAllSongs()
            .onEach { songs ->
                _uiState.update { it.copy(tracks = songs, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }
}
