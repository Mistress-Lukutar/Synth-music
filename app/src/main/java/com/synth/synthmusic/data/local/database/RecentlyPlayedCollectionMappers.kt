package com.synth.synthmusic.data.local.database

import com.synth.synthmusic.domain.model.CollectionType
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection

/**
 * Convert a database entity to a domain model.
 */
fun RecentlyPlayedCollectionEntity.toDomain(): RecentlyPlayedCollection = RecentlyPlayedCollection(
    id = id,
    type = CollectionType.valueOf(type.uppercase()),
    identifier = identifier,
    name = name,
    extra = extra,
    artworkUri = artworkUri,
    playedAt = playedAt
)

/**
 * Convert a domain model to a database entity.
 */
fun RecentlyPlayedCollection.toEntity(): RecentlyPlayedCollectionEntity = RecentlyPlayedCollectionEntity(
    id = id,
    type = type.name.lowercase(),
    identifier = identifier,
    name = name,
    extra = extra,
    artworkUri = artworkUri,
    playedAt = playedAt
)
