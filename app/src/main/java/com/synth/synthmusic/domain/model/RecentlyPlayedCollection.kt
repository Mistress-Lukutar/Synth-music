package com.synth.synthmusic.domain.model

/**
 * Domain model representing a recently played collection.
 *
 * @param id Database primary key.
 * @param type Type of collection.
 * @param identifier Unique identifier for the collection (album id, artist id, or playlist id string).
 * @param name Display name.
 * @param extra Auxiliary data (e.g. album artist for albums).
 * @param artworkUri Artwork URI snapshot at time of playback.
 * @param playedAt Timestamp when the collection was last played.
 */
data class RecentlyPlayedCollection(
    val id: Long = 0,
    val type: CollectionType,
    val identifier: String,
    val name: String,
    val extra: String?,
    val artworkUri: String?,
    val playedAt: Long
)

enum class CollectionType {
    ALBUM, ARTIST, PLAYLIST
}
