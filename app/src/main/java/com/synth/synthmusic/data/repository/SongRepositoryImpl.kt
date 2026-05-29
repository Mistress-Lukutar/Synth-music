package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.database.SongDao
import com.synth.synthmusic.data.local.database.toDomain
import com.synth.synthmusic.data.local.database.toEntity
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Implementation of [SongRepository] using Room.
 */
class SongRepositoryImpl(
    private val songDao: SongDao
) : SongRepository {

    override fun observeAllSongs(): Flow<List<Song>> = songDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    override fun observeSongsByAlbum(album: String): Flow<List<Song>> =
        songDao.observeByAlbum(album).map { list -> list.map { it.toDomain() } }

    override fun observeSongsByArtist(artist: String): Flow<List<Song>> =
        songDao.observeByArtist(artist).map { list -> list.map { it.toDomain() } }

    override fun observeSongsByFolder(folderPath: String): Flow<List<Song>> =
        songDao.observeByFolder(folderPath).map { list -> list.map { it.toDomain() } }

    override fun observeSongById(songId: String): Flow<Song?> =
        songDao.observeById(songId).map { it?.toDomain() }

    override suspend fun getSongById(songId: String): Song? =
        songDao.getById(songId)?.toDomain()

    override suspend fun getSongsByIds(songIds: List<String>): List<Song> =
        songDao.getByIds(songIds).map { it.toDomain() }

    override suspend fun saveSongs(songs: List<Song>) {
        songDao.insertAll(songs.map { it.toEntity() })
    }

    override suspend fun deleteSong(songId: String) = songDao.deleteById(songId)

    override suspend fun updateSongRating(songId: String, rating: Float) =
        songDao.updateRating(songId, rating)

    override suspend fun updateSongFavorite(songId: String, isFavorite: Boolean) =
        songDao.updateFavorite(songId, isFavorite)

    override suspend fun incrementPlayCount(songId: String) =
        songDao.incrementPlayCount(songId, System.currentTimeMillis())

    override suspend fun updateSongLyrics(songId: String, lyrics: String?) =
        songDao.updateLyrics(songId, lyrics)

    override suspend fun deleteAllSongs() = songDao.deleteAll()

    override fun observeFavoriteSongs(): Flow<List<Song>> =
        songDao.observeFavorites().map { list -> list.map { it.toDomain() } }

    override fun observeTopSongs(): Flow<List<Song>> =
        songDao.observeTopSongs().map { list -> list.map { it.toDomain() } }

    override fun observeRecentSongs(): Flow<List<Song>> =
        songDao.observeRecentSongs().map { list -> list.map { it.toDomain() } }

    override fun observeHistory(): Flow<List<Song>> =
        songDao.observeHistory().map { list -> list.map { it.toDomain() } }

    override fun observeFolders(): Flow<List<String>> = observeAllSongs()
        .map { songs ->
            songs.mapNotNull { File(it.path).parentFile?.absolutePath }
                .distinct()
                .sorted()
        }

    override fun searchSongs(query: String): Flow<List<Song>> =
        songDao.search(query).map { list -> list.map { it.toDomain() } }
}
