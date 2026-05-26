package com.synth.synthmusic.ui.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel for artist detail screen.
 */
class ArtistDetailViewModel(
    private val artistName: String,
    private val artistRepository: ArtistRepository,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _artist = MutableStateFlow<Artist?>(null)
    val artist: StateFlow<Artist?> = _artist.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    init {
        artistRepository.observeAllArtists()
            .onEach { artists ->
                _artist.value = artists.find { it.name == artistName }
            }
            .launchIn(viewModelScope)

        songRepository.observeSongsByArtist(artistName)
            .onEach { _songs.value = it }
            .launchIn(viewModelScope)
    }
}
