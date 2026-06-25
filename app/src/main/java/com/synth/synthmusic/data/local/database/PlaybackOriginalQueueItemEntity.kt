package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single item in the original (unshuffled) playback queue.
 *
 * Kept separate from the active queue so that the unshuffled order can be
 * restored after process death even while shuffle is enabled.
 */
@Entity(tableName = "playback_original_queue_items")
data class PlaybackOriginalQueueItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "song_id")
    val songId: String,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int
)
