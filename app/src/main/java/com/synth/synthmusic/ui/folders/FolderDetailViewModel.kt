package com.synth.synthmusic.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.PlaybackRepository
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel for the folder detail screen showing songs of a specific folder.
 *
 * When [isAll] is true, shows every track in the library sorted by date added
 * (newest first), acting as the fake "All Songs" folder.
 *
 * @param folderPath Path of the folder to display songs for.
 * @param isAll Whether to show all songs regardless of [folderPath].
 * @param songRepository Repository for song data.
 * @param playbackRepository Manager for audio playback.
 */
class FolderDetailViewModel(
    private val folderPath: String,
    val isAll: Boolean,
    private val songRepository: SongRepository,
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    val playbackState = playbackRepository.playbackState

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    init {
        val source = if (isAll) {
            songRepository.observeAllSongs()
                .map { list -> list.sortedByDescending { it.dateAdded } }
        } else {
            songRepository.observeSongsByFolder(folderPath)
        }
        source
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
        val tracks = _songs.value
        if (tracks.isNotEmpty()) {
            playbackRepository.playSongs(tracks, 0)
        }
    }

    fun shuffleAll() {
        val tracks = _songs.value.shuffled()
        if (tracks.isNotEmpty()) {
            playbackRepository.playSongs(tracks, 0)
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
