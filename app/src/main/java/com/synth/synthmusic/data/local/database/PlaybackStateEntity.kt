package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing the persisted playback state.
 */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1, // singleton row
    @ColumnInfo(name = "current_song_id")
    val currentSongId: String?,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,
    @ColumnInfo(name = "is_playing")
    val isPlaying: Boolean,
    @ColumnInfo(name = "repeat_mode")
    val repeatMode: Int,
    @ColumnInfo(name = "shuffle_mode")
    val shuffleMode: Boolean,
    @ColumnInfo(name = "queue_ids")
    val queueIds: String? = null
)
