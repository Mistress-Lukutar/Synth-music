package com.synth.synthmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.PlaybackRepository
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.RecentlyPlayedCollectionRepository
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the library screen managing tabs.
 */
class LibraryViewModel(
    private val songRepository: SongRepository,
    private val artistRepository: ArtistRepository,
    private val playbackRepository: PlaybackRepository,
    private val recentlyPlayedRepository: RecentlyPlayedCollectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        artistRepository.observeAllArtists()
            .onEach { artists ->
                _uiState.update { it.copy(artists = artists) }
            }
            .launchIn(viewModelScope)

        recentlyPlayedRepository.observeRecent()
            .onEach { list -> _uiState.update { it.copy(recentCollections = list) } }
            .launchIn(viewModelScope)

        songRepository.observeGenres()
            .onEach { genres -> _uiState.update { it.copy(genres = genres) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SelectTab -> {
                _uiState.update { it.copy(selectedTab = event.tab) }
            }
            is LibraryEvent.PlaySong -> playSong(event.songId)
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
