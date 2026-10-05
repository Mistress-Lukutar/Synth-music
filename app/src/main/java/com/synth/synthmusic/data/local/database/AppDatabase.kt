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
        RecentlyPlayedCollectionEntity::class,
        AiProviderEntity::class,
        AiModelEntity::class,
        AiChatEntity::class,
        AiChatMessageEntity::class,
        AiAssistantEntity::class,
        AiChatToolGrantEntity::class,
        AiActionLogEntity::class
    ],
    version = 14,
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

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN has_custom_artwork INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // AI assistant feature tables. Column definitions must match the
                // @Entity declarations exactly — a mismatch triggers destructive
                // migration and wipes the user's library.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ai_providers (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "label TEXT NOT NULL, " +
                    "protocol TEXT NOT NULL, " +
                    "base_url TEXT NOT NULL, " +
                    "api_key_encrypted TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ai_models (" +
                    "provider_id INTEGER NOT NULL, " +
                    "model_id TEXT NOT NULL, " +
                    "display_name TEXT NOT NULL, " +
                    "supports_tools INTEGER NOT NULL, " +
                    "supports_vision INTEGER NOT NULL, " +
                    "is_pinned INTEGER NOT NULL, " +
                    "PRIMARY KEY(provider_id, model_id))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ai_chats (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "assistant_id INTEGER, " +
                    "provider_id INTEGER, " +
                    "model_id TEXT, " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL, " +
                    "is_archived INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ai_chat_messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "chat_id INTEGER NOT NULL, " +
                    "role TEXT NOT NULL, " +
                    "content_json TEXT NOT NULL, " +
                    "status TEXT NOT NULL, " +
                    "input_tokens INTEGER, " +
                    "output_tokens INTEGER, " +
                    "created_at INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ai_chat_messages_chat_id " +
                    "ON ai_chat_messages (chat_id)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ai_assistants (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "builtin_key TEXT, " +
                    "name TEXT NOT NULL, " +
                    "description TEXT NOT NULL, " +
                    "system_prompt TEXT NOT NULL, " +
                    "avatar_icon TEXT NOT NULL, " +
                    "avatar_color_index INTEGER NOT NULL, " +
                    "default_grants_json TEXT NOT NULL, " +
                    "is_builtin INTEGER NOT NULL, " +
                    "sort_order INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ai_chat_tool_grants (" +
                    "chat_id INTEGER NOT NULL, " +
                    "capability TEXT NOT NULL, " +
                    "granted INTEGER NOT NULL, " +
                    "granted_at INTEGER NOT NULL, " +
                    "PRIMARY KEY(chat_id, capability))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ai_action_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "chat_id INTEGER, " +
                    "timestamp INTEGER NOT NULL, " +
                    "tool_name TEXT NOT NULL, " +
                    "args_json TEXT NOT NULL, " +
                    "outcome TEXT NOT NULL, " +
                    "summary TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ai_action_log_chat_id " +
                    "ON ai_action_log (chat_id)"
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
    abstract fun playbackOriginalQueueItemDao(): PlaybackOriginalQueueItemDao
    abstract fun waveformDataDao(): WaveformDataDao
    abstract fun recentlyPlayedCollectionDao(): RecentlyPlayedCollectionDao
    abstract fun aiProviderDao(): AiProviderDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun aiChatDao(): AiChatDao
    abstract fun aiChatMessageDao(): AiChatMessageDao
    abstract fun aiAssistantDao(): AiAssistantDao
    abstract fun aiChatToolGrantDao(): AiChatToolGrantDao
    abstract fun aiActionLogDao(): AiActionLogDao

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
