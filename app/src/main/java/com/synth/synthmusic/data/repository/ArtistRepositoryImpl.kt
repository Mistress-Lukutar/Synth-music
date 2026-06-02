package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.database.ArtistDao
import com.synth.synthmusic.data.local.database.ArtistEntity
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of [ArtistRepository] using Room.
 */
class ArtistRepositoryImpl(
    private val artistDao: ArtistDao
) : ArtistRepository {

    override fun observeAllArtists(): Flow<List<Artist>> = artistDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun saveArtists(artists: List<Artist>) {
        artistDao.insertAll(artists.map { it.toEntity() })
    }

    override suspend fun deleteAllArtists() = artistDao.deleteAll()

    override suspend fun replaceAllArtists(artists: List<Artist>) {
        artistDao.deleteAll()
        artistDao.insertAll(artists.map { it.toEntity() })
    }

    override suspend fun updateArtistArtwork(artistId: String, artworkUri: String?) {
        artistDao.updateArtworkUri(artistId, artworkUri)
    }
}

private fun ArtistEntity.toDomain(): Artist = Artist(
    id = id,
    name = name,
    songCount = songCount,
    albumCount = albumCount,
    artworkUri = artworkUri
)

private fun Artist.toEntity(): ArtistEntity = ArtistEntity(
    id = id,
    name = name,
    songCount = songCount,
    albumCount = albumCount,
    artworkUri = artworkUri
)
