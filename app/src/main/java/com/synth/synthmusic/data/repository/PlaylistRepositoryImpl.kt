package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.database.PlaylistDao
import com.synth.synthmusic.data.local.database.PlaylistEntity
import com.synth.synthmusic.data.local.database.PlaylistSongEntity
import com.synth.synthmusic.data.local.database.SongDao
import com.synth.synthmusic.domain.model.Playlist
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.synth.synthmusic.data.local.database.toDomain

/**
 * Implementation of [PlaylistRepository] using Room.
 */
class PlaylistRepositoryImpl(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) : PlaylistRepository {

    override fun observeAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.observeAll().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun createPlaylist(name: String): Long {
        val entity = PlaylistEntity(
            name = name,
            createdAt = System.currentTimeMillis(),
            songCount = 0
        )
        return playlistDao.insert(entity)
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) {
        val existing = playlistDao.getById(playlistId) ?: return
        playlistDao.update(existing.copy(name = name))
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deleteById(playlistId)
    }

    override fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> {
        return playlistDao.observeSongs(playlistId).map { playlistSongs ->
            playlistSongs.mapNotNull { ps ->
                songDao.getById(ps.songId)?.toDomain()
            }
        }
    }

    override suspend fun addSongToPlaylist(playlistId: Long, songId: String) {
        val currentSongs = playlistDao.observeSongs(playlistId).first()
        val maxPosition = currentSongs.maxOfOrNull { it.position } ?: -1
        playlistDao.insertSong(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                position = maxPosition + 1
            )
        )
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        playlistDao.deleteSong(playlistId, songId)
    }
}

private fun PlaylistEntity.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    createdAt = createdAt,
    songCount = songCount
)
