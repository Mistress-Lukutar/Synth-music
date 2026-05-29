package com.synth.synthmusic.data.repository

import com.synth.synthmusic.domain.model.Playlist
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakePlaylistRepository : PlaylistRepository {

    private val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private var nextId = 1L

    fun setPlaylists(value: List<Playlist>) {
        playlists.value = value
    }

    override fun observeAllPlaylists(): Flow<List<Playlist>> = playlists

    override suspend fun createPlaylist(name: String): Long {
        val id = nextId++
        playlists.value = playlists.value + Playlist(id = id, name = name, createdAt = 0, songCount = 0)
        return id
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) {
        playlists.value = playlists.value.map {
            if (it.id == playlistId) it.copy(name = name) else it
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlists.value = playlists.value.filter { it.id != playlistId }
    }

    override fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> = flowOf(emptyList())
    override suspend fun addSongToPlaylist(playlistId: Long, songId: String) {}
    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {}
    override suspend fun isSongInPlaylist(playlistId: Long, songId: String): Boolean = false
}
