package com.synth.synthmusic.domain.model

/**
 * A single position in the playback queue.
 *
 * The [id] is a stable, unique identifier for this queue position. It allows the
 * same [Song] to appear multiple times in the queue while still being manipulated
 * independently (e.g. remove one occurrence without affecting another).
 *
 * @param id stable identifier of the queue position.
 * @param song the queued song.
 */
data class QueueItem(
    val id: Long,
    val song: Song
)
