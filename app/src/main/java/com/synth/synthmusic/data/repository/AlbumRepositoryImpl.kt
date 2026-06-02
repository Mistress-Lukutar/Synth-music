package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.database.AlbumDao
import com.synth.synthmusic.data.local.database.AlbumEntity
import com.synth.synthmusic.domain.model.Album
import com.synth.synthmusic.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of [AlbumRepository] using Room.
 */
class AlbumRepositoryImpl(
    private val albumDao: AlbumDao
) : AlbumRepository {

    override fun observeAllAlbums(): Flow<List<Album>> = albumDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    override fun observeAlbumsByArtist(artist: String): Flow<List<Album>> =
        albumDao.observeByArtist(artist).map { list -> list.map { it.toDomain() } }

    override suspend fun saveAlbums(albums: List<Album>) {
        albumDao.insertAll(albums.map { it.toEntity() })
    }

    override suspend fun deleteAllAlbums() = albumDao.deleteAll()

    override suspend fun updateAlbumArtwork(albumId: String, artworkUri: String?) {
        albumDao.updateArtworkUri(albumId, artworkUri)
    }
}

private fun AlbumEntity.toDomain(): Album = Album(
    id = id,
    title = title,
    artist = artist,
    year = year,
    artworkUri = artworkUri,
    songCount = songCount,
    totalDurationMs = totalDurationMs
)

private fun Album.toEntity(): AlbumEntity = AlbumEntity(
    id = id,
    title = title,
    artist = artist,
    year = year,
    artworkUri = artworkUri,
    songCount = songCount,
    totalDurationMs = totalDurationMs
)
