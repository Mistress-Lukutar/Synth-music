package com.synth.synthmusic.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.MediaPlaybackManager
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel for folder detail screen showing songs in a directory.
 */
class FolderDetailViewModel(
    private val folderPath: String,
    private val songRepository: SongRepository,
    private val playbackManager: MediaPlaybackManager
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    init {
        songRepository.observeSongsByFolder(folderPath)
            .onEach { _songs.value = it }
            .launchIn(viewModelScope)
    }

    fun playSongAt(index: Int) {
        val tracks = _songs.value
        if (index in tracks.indices) {
            playbackManager.playSongs(tracks, index)
        }
    }

    fun playAll() {
        val tracks = _songs.value
        if (tracks.isNotEmpty()) {
            playbackManager.playSongs(tracks, 0)
        }
    }

    fun shuffleAll() {
        val tracks = _songs.value.shuffled()
        if (tracks.isNotEmpty()) {
            playbackManager.playSongs(tracks, 0)
        }
    }

    fun playNext(songId: String) {
        val song = _songs.value.find { it.id == songId } ?: return
        playbackManager.playNext(song)
    }

    fun addToQueue(songId: String) {
        val song = _songs.value.find { it.id == songId } ?: return
        playbackManager.addToQueue(song)
    }
}
