package com.synth.synthmusic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.PlaybackRepository
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the standalone search screen.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val songRepository: SongRepository,
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        playbackRepository.playbackState
            .onEach { playback ->
                _uiState.update { it.copy(currentSongId = playback.currentSongId) }
            }
            .launchIn(viewModelScope)

        _uiState
            .debounce(300)
            .flatMapLatest { state ->
                if (state.query.isBlank()) {
                    flowOf(emptyList())
                } else {
                    songRepository.searchSongs(state.query)
                }
            }
            .onEach { results -> _uiState.update { it.copy(results = results) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> {
                _uiState.update { it.copy(query = event.query) }
            }
            is SearchEvent.PlaySong -> playSong(event.songId)
        }
    }

    fun playNext(songId: String) {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId) ?: return@launch
            playbackRepository.playNext(song)
        }
    }

    fun addToQueue(songId: String) {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId) ?: return@launch
            playbackRepository.addToQueue(song)
        }
    }

    private fun playSong(songId: String) {
        viewModelScope.launch {
            val songs = songRepository.observeAllSongs().first()
            val index = songs.indexOfFirst { it.id == songId }
            if (index != -1) {
                playbackRepository.playSongs(songs, index)
            }
        }
    }
}
