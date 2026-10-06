package com.synth.synthmusic.data.ai.tools

import android.graphics.BitmapFactory
import com.synth.synthmusic.data.local.database.CoverBlacklistDao
import com.synth.synthmusic.data.local.database.CoverBlacklistEntity
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.usecase.ai.AiTool
import com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.security.MessageDigest

/** Embedded artwork of a song, decoded to bounds only. */
data class EmbeddedCover(
    val bytes: ByteArray,
    val width: Int,
    val height: Int
)

/** SHA-256 of the given bytes as lowercase hex — the cover blacklist key. */
internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

/**
 * Reads the embedded artwork of a song's audio file. Returns null when the
 * file is unreadable or carries no picture.
 */
internal suspend fun readEmbeddedCover(song: Song): EmbeddedCover? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = AudioFileIO.read(File(song.path)).tag?.firstArtwork?.binaryData
                ?: return@withContext null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            EmbeddedCover(
                bytes = bytes,
                width = options.outWidth.coerceAtLeast(0),
                height = options.outHeight.coerceAtLeast(0)
            )
        }.getOrNull()
    }

/** True for images whose proportions look like a site banner, not album art. */
internal fun isBannerShaped(width: Int, height: Int): Boolean {
    if (width <= 0 || height <= 0) return false
    val ratio = width.toDouble() / height
    return ratio >= BANNER_RATIO || ratio <= 1.0 / BANNER_RATIO
}

private const val BANNER_RATIO = 3.0

private fun stringList(args: JsonObject, name: String): List<String> =
    (args[name] as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        .orEmpty()

/**
 * `manage_cover_blacklist` — maintain the content-addressed blacklist of
 * garbage embedded covers (site banners etc.).
 *
 * `list` only needs READ_LIBRARY; `add`/`remove` additionally require the
 * WRITE_METADATA grant, enforced per action inside [execute] so read-only
 * chats can still inspect the blacklist.
 */
class ManageCoverBlacklistTool(
    private val blacklistDao: CoverBlacklistDao,
    private val readCover: suspend (Song) -> EmbeddedCover?,
    private val getSongById: suspend (String) -> Song?
) : AiTool {

    override val name = "manage_cover_blacklist"
    override val description = "Maintain the blacklist of garbage cover images (site " +
        "banners baked into downloaded files). Matching is by image content hash, so one " +
        "entry covers every track carrying the same picture. Actions: add (by songIds, " +
        "hashing each track's embedded cover; or by coverHashes from purge_covers/" +
        "audit results), remove (by coverHashes), list. After blacklisting, clean the " +
        "affected tracks with purge_covers."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    listOf("add", "remove", "list").forEach { add(JsonPrimitive(it)) }
                }
                put("description", "What to do with the blacklist")
            }
            putJsonObject("songIds") {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
                put("description", "add: tracks whose embedded covers to blacklist " +
                    "(e.g. the current track from get_playback_state)")
            }
            putJsonObject("coverHashes") {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
                put("description", "add/remove: cover content hashes (full 64-char hex)")
            }
            put("reason", ToolSchemas.queryProperty("Optional free-text reason, e.g. \"site banner\""))
        }
        putJsonArray("required") { add(JsonPrimitive("action")) }
    }
    override val requiredGrants = setOf(AiCapability.READ_LIBRARY)
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val action = args["action"]?.jsonPrimitive?.content
            ?: return ToolOutcome("Missing required parameter: action", isError = true)
        return when (action) {
            "list" -> list()
            "add" -> {
                if (AiCapability.WRITE_METADATA !in context.grants) {
                    return ToolOutcome(
                        "add requires the WRITE_METADATA grant (enable it in AI settings)",
                        isError = true
                    )
                }
                addToBlacklist(args)
            }
            "remove" -> {
                if (AiCapability.WRITE_METADATA !in context.grants) {
                    return ToolOutcome(
                        "remove requires the WRITE_METADATA grant (enable it in AI settings)",
                        isError = true
                    )
                }
                removeFromBlacklist(args)
            }
            else -> ToolOutcome("Unknown action: $action (use add, remove or list)", isError = true)
        }
    }

    private suspend fun addToBlacklist(args: JsonObject): ToolOutcome {
        val reason = args["reason"]?.jsonPrimitive?.content ?: "user"
        val now = System.currentTimeMillis()
        val failures = mutableListOf<String>()
        val added = mutableListOf<CoverBlacklistEntity>()
        var duplicates = 0

        val hashesFromArgs = stringList(args, "coverHashes")
        for (hash in hashesFromArgs) {
            val normalized = hash.trim().lowercase()
            if (!normalized.matches(Regex("[0-9a-f]{64}"))) {
                failures += "invalid hash: $hash"
                continue
            }
            if (blacklistDao.exists(normalized)) {
                duplicates++
                continue
            }
            val entry = CoverBlacklistEntity(
                hash = normalized,
                width = 0,
                height = 0,
                byteSize = 0,
                sourceSongId = null,
                reason = reason,
                blacklistedAt = now
            )
            blacklistDao.insert(entry)
            added += entry
        }

        val songs = stringList(args, "songIds").mapNotNull { id ->
            getSongById(id) ?: run {
                failures += "song not found: $id"
                null
            }
        }
        for (song in songs) {
            val cover = readCover(song)
            if (cover == null) {
                failures += "${song.title}: no embedded cover"
                continue
            }
            val hash = sha256Hex(cover.bytes)
            if (blacklistDao.exists(hash)) {
                duplicates++
                continue
            }
            val entry = CoverBlacklistEntity(
                hash = hash,
                width = cover.width,
                height = cover.height,
                byteSize = cover.bytes.size,
                sourceSongId = song.id,
                reason = reason,
                blacklistedAt = now
            )
            blacklistDao.insert(entry)
            added += entry
        }

        if (added.isEmpty() && failures.isEmpty() && duplicates > 0) {
            return ToolOutcome(buildJsonObject {
                put("added", 0)
                put("duplicates", duplicates)
                put("note", "All submitted covers were already blacklisted")
            }.toString())
        }
        return ToolOutcome(buildJsonObject {
            put("added", added.size)
            put("duplicates", duplicates)
            if (failures.isNotEmpty()) {
                put("failures", buildJsonArray { failures.forEach { add(it) } })
            }
            put("entries", buildJsonArray {
                added.forEach { entry ->
                    add(buildJsonObject {
                        put("hash", entry.hash)
                        put("width", entry.width)
                        put("height", entry.height)
                        put("byte_size", entry.byteSize)
                    })
                }
            })
        }.toString())
    }

    private suspend fun removeFromBlacklist(args: JsonObject): ToolOutcome {
        val hashes = stringList(args, "coverHashes").map { it.trim().lowercase() }
        if (hashes.isEmpty()) {
            return ToolOutcome("remove requires coverHashes (see manage_cover_blacklist list)", isError = true)
        }
        blacklistDao.deleteByHashes(hashes)
        return ToolOutcome(buildJsonObject {
            put("removed_hashes", hashes.size)
            put("note", "Removed from blacklist; files were NOT modified — run purge_covers to clean tracks")
        }.toString())
    }

    private suspend fun list(): ToolOutcome {
        val entries = blacklistDao.getAll()
        return ToolOutcome(buildJsonObject {
            put("count", entries.size)
            put("entries", buildJsonArray {
                entries.forEach { entry ->
                    add(buildJsonObject {
                        put("hash", entry.hash)
                        put("width", entry.width)
                        put("height", entry.height)
                        put("byte_size", entry.byteSize)
                        entry.sourceSongId?.let { sourceId ->
                            getSongById(sourceId)?.let { song ->
                                put("source_track", songSummary(song))
                            }
                        }
                        put("reason", entry.reason)
                        put("blacklisted_at", entry.blacklistedAt)
                    })
                }
            })
        }.toString())
    }
}

/**
 * `purge_covers` — remove blacklisted embedded covers from audio files.
 *
 * Matching is by cover content hash against the `manage_cover_blacklist`
 * list. Always run with dry_run=true first: the report groups the matches
 * by hash so the model can sanity-check (count, dimensions, banner shape)
 * before deleting. Actual deletion reuses the artwork removal path, which
 * also clears the cover cache and the database artwork URI.
 */
class PurgeCoversTool(
    private val blacklistDao: CoverBlacklistDao,
    private val allSongs: suspend () -> List<Song>,
    private val playlistSongs: suspend (Long) -> List<Song>,
    private val readCover: suspend (Song) -> EmbeddedCover?,
    private val removeCover: suspend (Song) -> Result<Unit>
) : AiTool {

    override val name = "purge_covers"
    override val description = "Remove blacklisted garbage covers (site banners etc.) from " +
        "embedded artwork in a scope (whole library or one playlist; playlist ids come from " +
        "browse_collections). ALWAYS call with dry_run=true first and review the matched " +
        "groups (count, dimensions, banner_shaped) before running with dry_run=false, " +
        "which deletes the artwork from the files. Only covers whose image hash is on the " +
        "manage_cover_blacklist list are removed."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("scope") {
                put("type", "string")
                putJsonArray("enum") {
                    listOf("all", "playlist").forEach { add(JsonPrimitive(it)) }
                }
                put("description", "\"all\" library or one \"playlist\"")
            }
            putJsonObject("playlistId") {
                put("type", "integer")
                put("description", "Playlist id, required when scope=playlist")
            }
            putJsonObject("dry_run") {
                put("type", "boolean")
                put("description", "true (default): report matches without deleting")
            }
            put("limit", ToolSchemas.intProperty("Max tracks to process", 1, MAX_TRACKS))
        }
        putJsonArray("required") { add(JsonPrimitive("scope")) }
    }
    override val requiredGrants = setOf(AiCapability.WRITE_METADATA)
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val scope = args["scope"]?.jsonPrimitive?.content ?: ""
        val songs = when (scope) {
            "all" -> allSongs()
            "playlist" -> {
                val playlistId = args["playlistId"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: return ToolOutcome(
                        "scope=playlist requires a numeric playlistId " +
                            "(get ids from browse_collections type=playlists)",
                        isError = true
                    )
                playlistSongs(playlistId)
            }
            else -> return ToolOutcome(
                "Unknown scope: $scope (use \"all\" or \"playlist\")",
                isError = true
            )
        }
        val dryRun = args["dry_run"]?.jsonPrimitive?.content != "false"
        val limit = (args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: DEFAULT_TRACKS)
            .coerceIn(1, MAX_TRACKS)

        val matches = songs.mapNotNull { song ->
            val cover = readCover(song) ?: return@mapNotNull null
            val hash = sha256Hex(cover.bytes)
            if (blacklistDao.exists(hash)) song to CoverMatch(hash, cover) else null
        }

        val groups = matches
            .groupBy({ it.second.hash }, { it.first to it.second })
            .map { (hash, items) ->
                CoverGroup(
                    hash = hash,
                    width = items.first().second.cover.width,
                    height = items.first().second.cover.height,
                    byteSize = items.first().second.cover.bytes.size,
                    songs = items.map { it.first }
                )
            }
            .sortedByDescending { it.songs.size }

        if (dryRun) {
            val total = groups.sumOf { it.songs.size }
            return ToolOutcome(buildJsonObject {
                put("dry_run", true)
                put("scope", scope)
                put("matched_tracks", total)
                put("groups", buildJsonArray {
                    groups.take(MAX_GROUPS).forEach { group ->
                        add(buildJsonObject {
                            put("hash", group.hash)
                            put("width", group.width)
                            put("height", group.height)
                            put("byte_size", group.byteSize)
                            put("track_count", group.songs.size)
                            put("banner_shaped", isBannerShaped(group.width, group.height))
                            putJsonArray("songs") {
                                group.songs.take(SONGS_PER_GROUP).forEach {
                                    add(songSummary(it))
                                }
                            }
                        })
                    }
                })
                if (groups.size > MAX_GROUPS) {
                    put("note", "Showing $MAX_GROUPS of ${groups.size} groups")
                }
                put("hint", "Call again with dry_run=false to delete these covers from the files")
            }.toString())
        }

        var updated = 0
        val failures = mutableListOf<String>()
        for ((song, _) in matches.take(limit)) {
            removeCover(song).fold(
                onSuccess = { updated++ },
                onFailure = { e -> failures += "${song.title}: ${e.message}" }
            )
        }
        return ToolOutcome(buildJsonObject {
            put("dry_run", false)
            put("updated", updated)
            put("failed", failures.size)
            if (failures.isNotEmpty()) {
                put("failures", buildJsonArray { failures.forEach { add(it) } })
            }
            if (matches.size > limit) {
                put("note", "Processed $limit of ${matches.size} matched tracks; " +
                    "call again to continue")
            }
        }.toString())
    }

    private data class CoverMatch(val hash: String, val cover: EmbeddedCover)

    private data class CoverGroup(
        val hash: String,
        val width: Int,
        val height: Int,
        val byteSize: Int,
        val songs: List<Song>
    )

    private companion object {
        const val MAX_TRACKS = 500
        const val DEFAULT_TRACKS = 200
        const val MAX_GROUPS = 20
        const val SONGS_PER_GROUP = 5
    }
}
