package com.synth.synthmusic.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.PlaybackRepository
import com.synth.synthmusic.domain.model.CollectionType
import com.synth.synthmusic.domain.model.Playlist
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.data.local.cover.CoverCache
import com.synth.synthmusic.domain.repository.RecentlyPlayedCollectionRepository
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for playlist detail screen.
 */
class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val playlistRepository: PlaylistRepository,
    private val playbackRepository: PlaybackRepository,
    private val recentlyPlayedRepository: RecentlyPlayedCollectionRepository,
    private val coverCache: CoverCache
) : ViewModel() {

    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    val playbackState = playbackRepository.playbackState

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    init {
        playlistRepository.observeAllPlaylists()
            .onEach { list ->
                _playlist.value = list.find { it.id == playlistId }
            }
            .launchIn(viewModelScope)

        playlistRepository.observePlaylistSongs(playlistId)
            .onEach { list -> _songs.value = list }
            .launchIn(viewModelScope)
    }

    fun playSongAt(index: Int) {
        val tracks = _songs.value
        if (index in tracks.indices) {
            playbackRepository.playSongs(tracks, index)
            recordPlaylistPlayed()
        }
    }

    fun playAll() {
        val tracks = _songs.value
        if (tracks.isNotEmpty()) {
            playbackRepository.playSongs(tracks, 0)
            recordPlaylistPlayed()
        }
    }

    fun shuffleAll() {
        val tracks = _songs.value.shuffled()
        if (tracks.isNotEmpty()) {
            playbackRepository.playSongs(tracks, 0)
            recordPlaylistPlayed()
        }
    }

    private fun recordPlaylistPlayed() {
        val pl = _playlist.value ?: return
        viewModelScope.launch {
            recentlyPlayedRepository.recordPlayed(
                RecentlyPlayedCollection(
                    type = CollectionType.PLAYLIST,
                    identifier = pl.id.toString(),
                    name = pl.name,
                    extra = null,
                    artworkUri = _songs.value.firstOrNull()?.artworkUri,
                    playedAt = System.currentTimeMillis()
                )
            )
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

    fun removeSong(songId: String) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun updateArtwork(bytes: ByteArray?) {
        viewModelScope.launch {
            val playlist = _playlist.value ?: return@launch
            if (bytes != null) {
                val file = coverCache.saveCustomCover(CoverCache.Type.PLAYLIST, playlist.id.toString(), bytes)
                playlistRepository.updatePlaylistArtwork(playlist.id, Uri.fromFile(file).toString())
            } else {
                coverCache.deleteCover(CoverCache.Type.PLAYLIST, playlist.id.toString())
                playlistRepository.updatePlaylistArtwork(playlist.id, null)
            }
        }
    }

    fun renamePlaylist(name: String) {
        val playlist = _playlist.value ?: return
        if (playlist.isFixed) return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlist.id, name)
        }
    }

    fun deletePlaylist() {
        val playlist = _playlist.value ?: return
        if (playlist.isFixed) return
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist.id)
        }
    }
}
