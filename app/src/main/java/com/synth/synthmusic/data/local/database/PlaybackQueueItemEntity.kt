package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single item in the current playback queue.
 *
 * Stored as a normalized table so that queue order is guaranteed
 * and individual items can be updated efficiently.
 */
@Entity(tableName = "playback_queue_items")
data class PlaybackQueueItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "song_id")
    val songId: String,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int
)
