package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.Playlist
import com.synth.synthmusic.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for playlist operations.
 */
interface PlaylistRepository {
    fun observeAllPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, name: String)
    suspend fun deletePlaylist(playlistId: Long)
    fun observePlaylistSongs(playlistId: Long): Flow<List<Song>>
    suspend fun addSongToPlaylist(playlistId: Long, songId: String)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)
    suspend fun isSongInPlaylist(playlistId: Long, songId: String): Boolean
    suspend fun updatePlaylistArtwork(playlistId: Long, artworkUri: String?)
    suspend fun ensureFavoritesPlaylist(): Long
    suspend fun getFavoritesPlaylistId(): Long?
    suspend fun ensureHistoryPlaylist(): Long
    suspend fun ensureTopTracksPlaylist(): Long
    suspend fun recordPlayAndSyncPlaylists(songId: String)
    fun observeFixedPlaylists(): Flow<List<Playlist>>
}
