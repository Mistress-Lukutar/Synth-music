package com.synth.synthmusic.ui.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.PlaybackRepository
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for genre detail screen showing all songs of a specific genre.
 *
 * @param genre the genre name to display songs for.
 * @param songRepository repository for song data.
 * @param playbackManager manager for audio playback.
 */
class GenreDetailViewModel(
    private val genre: String,
    private val songRepository: SongRepository,
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    val playbackState = playbackRepository.playbackState

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    init {
        songRepository.observeSongsByGenre(genre)
            .onEach { _songs.value = it }
            .launchIn(viewModelScope)
    }

    fun playSongAt(index: Int) {
        val tracks = _songs.value
        if (index in tracks.indices) {
            playbackRepository.playSongs(tracks, index)
        }
    }

    fun playAll() {
        viewModelScope.launch {
            val tracks = songRepository.observeSongsByGenre(genre).first()
            if (tracks.isNotEmpty()) {
                playbackRepository.playSongs(tracks, 0)
            }
        }
    }

    fun shuffleAll() {
        viewModelScope.launch {
            val tracks = songRepository.observeSongsByGenre(genre).first().shuffled()
            if (tracks.isNotEmpty()) {
                playbackRepository.playSongs(tracks, 0)
            }
        }
    }

    fun playNext(songId: String) {
        val song = _songs.value.find { it.id == songId } ?: return
        playbackRepository.playNext(song)
    }

    fun addToQueue(songId: String) {
        val song = _songs.value.find { it.id == songId } ?: return
        playbackRepository.addToQueue(song)
    }
}
