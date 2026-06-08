package com.synth.synthmusic.ui.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.model.Album
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.model.CollectionType
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import com.synth.synthmusic.domain.repository.AlbumRepository
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.RecentlyPlayedCollectionRepository
import com.synth.synthmusic.data.local.cover.CoverCache
import com.synth.synthmusic.domain.repository.SongRepository
import android.net.Uri
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
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackRepository: com.synth.synthmusic.data.media.PlaybackRepository,
    private val recentlyPlayedRepository: RecentlyPlayedCollectionRepository,
    private val coverCache: CoverCache
) : ViewModel() {

    private val _artist = MutableStateFlow<Artist?>(null)
    val artist: StateFlow<Artist?> = _artist.asStateFlow()

    val playbackState = playbackRepository.playbackState

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    fun playSongAt(index: Int) {
        val tracks = _songs.value
        if (index in tracks.indices) {
            playbackRepository.playSongs(tracks, index)
            recordArtistPlayed()
        }
    }

    fun playAll() {
        viewModelScope.launch {
            val tracks = songRepository.observeSongsByArtist(artistName).first()
            if (tracks.isNotEmpty()) {
                playbackRepository.playSongs(tracks, 0)
                recordArtistPlayed()
            }
        }
    }

    fun shuffleAll() {
        viewModelScope.launch {
            val tracks = songRepository.observeSongsByArtist(artistName).first().shuffled()
            if (tracks.isNotEmpty()) {
                playbackRepository.playSongs(tracks, 0)
                recordArtistPlayed()
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

    fun updateArtwork(bytes: ByteArray?) {
        viewModelScope.launch {
            val artist = _artist.value ?: return@launch
            if (bytes != null) {
                val file = coverCache.saveCustomCover(CoverCache.Type.ARTIST, artist.id, bytes)
                artistRepository.updateArtistArtwork(artist.id, Uri.fromFile(file).toString())
            } else {
                coverCache.deleteCover(CoverCache.Type.ARTIST, artist.id)
                artistRepository.updateArtistArtwork(artist.id, null)
            }
        }
    }

    private fun recordArtistPlayed() {
        val art = _artist.value ?: return
        viewModelScope.launch {
            recentlyPlayedRepository.recordPlayed(
                RecentlyPlayedCollection(
                    type = CollectionType.ARTIST,
                    identifier = art.id,
                    name = art.name,
                    extra = null,
                    artworkUri = _songs.value.firstOrNull()?.artworkUri,
                    playedAt = System.currentTimeMillis()
                )
            )
        }
    }

    init {
        artistRepository.observeAllArtists()
            .onEach { artists ->
                _artist.value = artists.find { it.name == artistName }
            }
            .launchIn(viewModelScope)

        albumRepository.observeAlbumsByArtist(artistName)
            .onEach { _albums.value = it }
            .launchIn(viewModelScope)

        songRepository.observeSongsByArtist(artistName)
            .onEach { _songs.value = it }
            .launchIn(viewModelScope)
    }
}
