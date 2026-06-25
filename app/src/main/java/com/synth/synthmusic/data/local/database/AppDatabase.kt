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
        PlaybackOriginalQueueItemEntity::class,
        WaveformDataEntity::class,
        RecentlyPlayedCollectionEntity::class
    ],
    version = 12,
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

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate playback_queue_items with stable position id and both order columns.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS playback_queue_items_new (" +
                    "id INTEGER PRIMARY KEY NOT NULL, " +
                    "song_id TEXT NOT NULL, " +
                    "active_order_index INTEGER NOT NULL, " +
                    "original_order_index INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO playback_queue_items_new (id, song_id, active_order_index, original_order_index) " +
                    "SELECT ROWID, song_id, order_index, order_index FROM playback_queue_items"
                )
                db.execSQL("DROP TABLE playback_queue_items")
                db.execSQL("ALTER TABLE playback_queue_items_new RENAME TO playback_queue_items")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Split the combined queue table back into active and original queue tables.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS playback_queue_items_new (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "song_id TEXT NOT NULL, " +
                    "order_index INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO playback_queue_items_new (song_id, order_index) " +
                    "SELECT song_id, active_order_index FROM playback_queue_items ORDER BY active_order_index ASC"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS playback_original_queue_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "song_id TEXT NOT NULL, " +
                    "order_index INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO playback_original_queue_items (song_id, order_index) " +
                    "SELECT song_id, original_order_index FROM playback_queue_items ORDER BY original_order_index ASC"
                )
                db.execSQL("DROP TABLE playback_queue_items")
                db.execSQL("ALTER TABLE playback_queue_items_new RENAME TO playback_queue_items")
            }
        }
    }
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun playbackQueueItemDao(): PlaybackQueueItemDao
    abstract fun playbackOriginalQueueItemDao(): PlaybackOriginalQueueItemDao
    abstract fun waveformDataDao(): WaveformDataDao
    abstract fun recentlyPlayedCollectionDao(): RecentlyPlayedCollectionDao

    /**
     * Atomically persists playback state and both queue views as a single transaction.
     * Guarantees that [playback_state], [playback_queue_items] and
     * [playback_original_queue_items] are always consistent.
     */
    @Transaction
    suspend fun savePlaybackState(
        state: PlaybackStateEntity,
        activeQueue: List<PlaybackQueueItemEntity>,
        originalQueue: List<PlaybackOriginalQueueItemEntity>
    ) {
        playbackStateDao().insert(state)
        playbackQueueItemDao().replaceAll(activeQueue)
        playbackOriginalQueueItemDao().replaceAll(originalQueue)
    }
}
