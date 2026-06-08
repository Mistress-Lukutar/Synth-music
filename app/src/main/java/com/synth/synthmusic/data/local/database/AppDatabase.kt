package com.synth.synthmusic.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Main Room database for the application.
 */
@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlaybackStateEntity::class,
        PlaybackQueueItemEntity::class,
        WaveformDataEntity::class,
        RecentlyPlayedCollectionEntity::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE artists ADD COLUMN artwork_uri TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN is_fixed INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS playback_queue_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "song_id TEXT NOT NULL, " +
                    "order_index INTEGER NOT NULL)"
                )
            }
        }
    }
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun playbackQueueItemDao(): PlaybackQueueItemDao
    abstract fun waveformDataDao(): WaveformDataDao
    abstract fun recentlyPlayedCollectionDao(): RecentlyPlayedCollectionDao

    /**
     * Atomically persists playback state and queue as a single transaction.
     * Guarantees that [playback_state] and [playback_queue_items] are always consistent.
     */
    @Transaction
    suspend fun savePlaybackState(state: PlaybackStateEntity, queue: List<PlaybackQueueItemEntity>) {
        playbackStateDao().insert(state)
        playbackQueueItemDao().clearAll()
        if (queue.isNotEmpty()) {
            playbackQueueItemDao().insertAll(queue)
        }
    }
}
