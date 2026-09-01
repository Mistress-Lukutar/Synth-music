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
            songCount = 0,
            artworkUri = null
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
        return playlistDao.observePlaylistSongEntities(playlistId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addSongToPlaylist(playlistId: Long, songId: String) {
        val currentSongs = playlistDao.observeSongs(playlistId).first()
        if (currentSongs.any { it.songId == songId }) return

        val maxPosition = currentSongs.maxOfOrNull { it.position } ?: -1
        playlistDao.insertSong(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                position = maxPosition + 1
            )
        )

        val entity = playlistDao.getById(playlistId) ?: return
        val song = songDao.getById(songId)
        playlistDao.update(
            entity.copy(
                songCount = currentSongs.size + 1,
                artworkUri = entity.artworkUri ?: song?.artworkUri
            )
        )
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        playlistDao.deleteSong(playlistId, songId)

        val currentSongs = playlistDao.observeSongs(playlistId).first()
        val entity = playlistDao.getById(playlistId) ?: return
        // A user-picked cover survives song removals; only auto-derived artwork
        // is recomputed from the remaining first track.
        val newArtwork = if (entity.hasCustomArtwork) {
            entity.artworkUri
        } else if (currentSongs.isNotEmpty()) {
            songDao.getById(currentSongs.first().songId)?.artworkUri
        } else null

        playlistDao.update(
            entity.copy(
                songCount = currentSongs.size,
                artworkUri = newArtwork
            )
        )
    }

    override suspend fun isSongInPlaylist(playlistId: Long, songId: String): Boolean {
        return playlistDao.hasSong(playlistId, songId)
    }

    override suspend fun updatePlaylistArtwork(playlistId: Long, artworkUri: String?) {
        playlistDao.updateArtworkUri(playlistId, artworkUri, hasCustomArtwork = artworkUri != null)
    }

    override suspend fun ensureFavoritesPlaylist(): Long {
        val existing = playlistDao.getFixedPlaylistByName("Favorites")
        return existing?.id ?: playlistDao.insert(
            PlaylistEntity(
                name = "Favorites",
                createdAt = System.currentTimeMillis(),
                songCount = 0,
                artworkUri = null,
                isFixed = true
            )
        )
    }

    override suspend fun getFavoritesPlaylistId(): Long? {
        return playlistDao.getFixedPlaylistByName("Favorites")?.id
    }

    override suspend fun ensureHistoryPlaylist(): Long {
        val existing = playlistDao.getFixedPlaylistByName("History")
        return existing?.id ?: playlistDao.insert(
            PlaylistEntity(
                name = "History",
                createdAt = System.currentTimeMillis(),
                songCount = 0,
                artworkUri = null,
                isFixed = true
            )
        )
    }

    override suspend fun ensureTopTracksPlaylist(): Long {
        val existing = playlistDao.getFixedPlaylistByName("Top Tracks")
        return existing?.id ?: playlistDao.insert(
            PlaylistEntity(
                name = "Top Tracks",
                createdAt = System.currentTimeMillis(),
                songCount = 0,
                artworkUri = null,
                isFixed = true
            )
        )
    }

    override suspend fun recordPlayAndSyncPlaylists(songId: String) {
        val timestamp = System.currentTimeMillis()
        songDao.incrementPlayCount(songId, timestamp)

        val historyId = ensureHistoryPlaylist()
        val topId = ensureTopTracksPlaylist()

        // Sync History: last 50 played
        playlistDao.deleteAllSongs(historyId)
        val historySongs = songDao.observeHistory(50).first()
        historySongs.forEachIndexed { index, song ->
            playlistDao.insertSong(
                PlaylistSongEntity(historyId, song.id, index)
            )
        }

        // Sync Top: top 50 by play count
        playlistDao.deleteAllSongs(topId)
        val topSongs = songDao.observeTopSongs(50).first()
        topSongs.forEachIndexed { index, song ->
            playlistDao.insertSong(
                PlaylistSongEntity(topId, song.id, index)
            )
        }

        // Update playlist metadata. Custom covers are never overwritten by the
        // per-play sync; auto artwork follows the newest first track.
        val historyEntity = playlistDao.getById(historyId)
        if (historyEntity != null) {
            playlistDao.update(
                historyEntity.copy(
                    songCount = historySongs.size,
                    artworkUri = if (historyEntity.hasCustomArtwork) {
                        historyEntity.artworkUri
                    } else {
                        historySongs.firstOrNull()?.artworkUri ?: historyEntity.artworkUri
                    }
                )
            )
        }

        val topEntity = playlistDao.getById(topId)
        if (topEntity != null) {
            playlistDao.update(
                topEntity.copy(
                    songCount = topSongs.size,
                    artworkUri = if (topEntity.hasCustomArtwork) {
                        topEntity.artworkUri
                    } else {
                        topSongs.firstOrNull()?.artworkUri ?: topEntity.artworkUri
                    }
                )
            )
        }
    }

    override fun observeFixedPlaylists(): Flow<List<Playlist>> =
        playlistDao.observeFixedPlaylists().map { list ->
            list.map { it.toDomain() }
        }
}

private fun PlaylistEntity.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    createdAt = createdAt,
    songCount = songCount,
    artworkUri = artworkUri,
    hasCustomArtwork = hasCustomArtwork,
    isFixed = isFixed
)
