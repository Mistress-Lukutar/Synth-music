package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.database.ArtistDao
import com.synth.synthmusic.data.local.database.ArtistEntity
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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
        // Preserve user/AI-written bios across rescans. Room's REPLACE conflict
        // strategy may overwrite rows, wiping the new bio column on migrations.
        withContext(Dispatchers.IO) {
            val existingBios = artistDao.getAllOnce().associate { it.id to it.bio }
            artistDao.insertAll(artists.map { it.toEntity() })
            for (artist in artists) {
                val oldBio = existingBios[artist.id]
                if (oldBio != null) {
                    artistDao.updateBio(artist.id, oldBio)
                }
            }
        }
    }

    override suspend fun deleteAllArtists() = artistDao.deleteAll()

    override suspend fun replaceAllArtists(artists: List<Artist>) {
        // Preserve user/AI-written bios across rescans. REPLACE strategy wipes
        // rows, so we snapshot and restore bio values for matching artists.
        withContext(Dispatchers.IO) {
            val existingBios = artistDao.getAllOnce().associate { it.id to it.bio }
            artistDao.deleteAll()
            artistDao.insertAll(artists.map { it.toEntity() })
            for (artist in artists) {
                val oldBio = existingBios[artist.id]
                if (oldBio != null) {
                    artistDao.updateBio(artist.id, oldBio)
                }
            }
        }
    }

    override suspend fun updateArtistArtwork(artistId: String, artworkUri: String?) {
        artistDao.updateArtworkUri(artistId, artworkUri)
    }

    override suspend fun updateArtistBio(artistId: String, bio: String?) {
        artistDao.updateBio(artistId, bio)
    }
}

private fun ArtistEntity.toDomain(): Artist = Artist(
    id = id,
    name = name,
    songCount = songCount,
    albumCount = albumCount,
    artworkUri = artworkUri,
    bio = bio
)

private fun Artist.toEntity(): ArtistEntity = ArtistEntity(
    id = id,
    name = name,
    songCount = songCount,
    albumCount = albumCount,
    artworkUri = artworkUri,
    bio = bio
)
