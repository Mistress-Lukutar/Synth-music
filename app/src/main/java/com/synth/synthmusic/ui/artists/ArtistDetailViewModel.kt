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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for artist detail screen.
 */
class ArtistDetailViewModel(
    private val artistName: String,
    private val artistRepository: ArtistRepository,
    private val songRepository: SongRepository,
    private val playbackManager: com.synth.synthmusic.data.media.MediaPlaybackManager
) : ViewModel() {

    private val _artist = MutableStateFlow<Artist?>(null)
    val artist: StateFlow<Artist?> = _artist.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    fun playSongAt(index: Int) {
        val tracks = _songs.value
        if (index in tracks.indices) {
            playbackManager.playSongs(tracks, index)
        }
    }

    fun playAll() {
        viewModelScope.launch {
            val tracks = songRepository.observeSongsByArtist(artistName).first()
            if (tracks.isNotEmpty()) {
                playbackManager.playSongs(tracks, 0)
            }
        }
    }

    fun shuffleAll() {
        viewModelScope.launch {
            val tracks = songRepository.observeSongsByArtist(artistName).first().shuffled()
            if (tracks.isNotEmpty()) {
                playbackManager.playSongs(tracks, 0)
            }
        }
    }

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
