package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.database.RecentlyPlayedCollectionDao
import com.synth.synthmusic.data.local.database.toDomain
import com.synth.synthmusic.data.local.database.toEntity
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import com.synth.synthmusic.domain.repository.RecentlyPlayedCollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of [RecentlyPlayedCollectionRepository] backed by Room.
 */
class RecentlyPlayedCollectionRepositoryImpl(
    private val dao: RecentlyPlayedCollectionDao
) : RecentlyPlayedCollectionRepository {

    override suspend fun recordPlayed(collection: RecentlyPlayedCollection) {
        val existing = dao.findByTypeAndIdentifier(
            type = collection.type.name.lowercase(),
            identifier = collection.identifier
        )
        if (existing != null) {
            dao.update(
                existing.copy(
                    name = collection.name,
                    extra = collection.extra,
                    artworkUri = collection.artworkUri,
                    playedAt = collection.playedAt
                )
            )
        } else {
            dao.insert(collection.toEntity())
        }
        dao.trimOld()
    }

    override fun observeRecent(): Flow<List<RecentlyPlayedCollection>> {
        return dao.observeRecent().map { list ->
            list.map { it.toDomain() }
        }
    }
}
