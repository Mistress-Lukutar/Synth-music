package com.synth.synthmusic.data.ai.tools

import android.media.MediaScannerConnection
import com.synth.synthmusic.data.local.cover.CoverCache
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.UpdateMetadataUseCase
import com.synth.synthmusic.domain.usecase.WriteArtworkToMp3UseCase
import com.synth.synthmusic.domain.usecase.ai.AiTool
import com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.resume
import java.net.URL

/** Max files touched per tool call (rate limit enforced in tools). */
private const val MAX_FILES_PER_CALL = 50

/**
 * Parses a JSON string array argument defensively.
 */
internal fun stringArray(args: JsonObject, key: String): List<String> =
    (args[key] as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content }
        .orEmpty()

/**
 * `update_songs_metadata` — batch metadata editor over
 * [UpdateMetadataUseCase], reporting per-file failures.
 */
class UpdateSongsMetadataTool(
    private val appContext: android.content.Context,
    private val songRepository: SongRepository,
    private val updateMetadata: UpdateMetadataUseCase
) : AiTool {

    override val name = "update_songs_metadata"
    override val description = "Update ID3 metadata of up to 50 songs in one call. Only " +
        "provided fields are changed. After writing, the database row and MediaStore are " +
        "refreshed. Requires Write metadata permission and shows an approval card."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("updates") {
                put("type", "array")
                put("description", "List of per-song updates (max 50)")
                put("items", buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        put("songId", buildJsonObject { put("type", "string") })
                        put("title", buildJsonObject { put("type", "string") })
                        put("artist", buildJsonObject { put("type", "string") })
                        put("album", buildJsonObject { put("type", "string") })
                        put("albumArtist", buildJsonObject { put("type", "string") })
                        put("genre", buildJsonObject { put("type", "string") })
                        put("year", buildJsonObject { put("type", "integer") })
                        put("track", buildJsonObject { put("type", "integer") })
                        put("comment", buildJsonObject { put("type", "string") })
                    }
                    putJsonArray("required") { add(JsonPrimitive("songId")) }
                })
            }
        }
        putJsonArray("required") { add(JsonPrimitive("updates")) }
    }
    override val requiredGrants = setOf(AiCapability.WRITE_METADATA)
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override fun summarize(args: JsonObject): String {
        val updates = (args["updates"] as? kotlinx.serialization.json.JsonArray).orEmpty()
        return "Edit metadata of ${updates.size} file(s)"
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val updates = (args["updates"] as? kotlinx.serialization.json.JsonArray).orEmpty()
        if (updates.isEmpty()) {
            return ToolOutcome("Missing required parameter: updates", isError = true)
        }
        if (updates.size > MAX_FILES_PER_CALL) {
            return ToolOutcome(
                "Rate limit: max $MAX_FILES_PER_CALL files per call", isError = true
            )
        }
        var ok = 0
        val failures = mutableListOf<String>()
        for (raw in updates) {
            val obj = raw as? JsonObject ?: continue
            val songId = obj["songId"]?.jsonPrimitive?.content ?: continue
            val song = songRepository.getSongById(songId)
            if (song == null) {
                failures.add("$songId: unknown song id")
                continue
            }
            val result = updateMetadata(
                song,
                title = obj["title"]?.jsonPrimitive?.content,
                artist = obj["artist"]?.jsonPrimitive?.content,
                album = obj["album"]?.jsonPrimitive?.content,
                albumArtist = obj["albumArtist"]?.jsonPrimitive?.content,
                genre = obj["genre"]?.jsonPrimitive?.content,
                year = obj["year"]?.jsonPrimitive?.content,
                trackNumber = obj["track"]?.jsonPrimitive?.content,
                comment = obj["comment"]?.jsonPrimitive?.content
            )
            if (result.isSuccess) {
                ok++
                scanFiles(appContext, listOf(song.path))
            } else {
                failures.add(
                    "${song.title}: ${result.exceptionOrNull()?.message ?: "write failed"}"
                )
            }
        }
        return ToolOutcome(
            buildJsonObject {
                put("updated", ok)
                put("failed", failures.size)
                if (failures.isNotEmpty()) {
                    put("failures", buildJsonArray { failures.forEach { add(JsonPrimitive(it)) } })
                }
            }.toString(),
            isError = failures.isNotEmpty() && ok == 0
        )
    }
}

/**
 * `set_lyrics` — writes lyrics to the Room record and the ID3 USLT tag.
 */
class SetLyricsTool(
    private val appContext: android.content.Context,
    private val songRepository: SongRepository,
    private val updateMetadata: UpdateMetadataUseCase
) : AiTool {

    override val name = "set_lyrics"
    override val description = "Set lyrics for one song. Writes both the app database and the " +
        "ID3 USLT tag. Synced lyrics with [mm:ss.xx] timestamps are stored as-is."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("songId", buildJsonObject { put("type", "string") })
            put("lyrics", buildJsonObject { put("type", "string") })
        }
        putJsonArray("required") {
            add(JsonPrimitive("songId")); add(JsonPrimitive("lyrics"))
        }
    }
    override val requiredGrants = setOf(AiCapability.WRITE_METADATA)
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override fun summarize(args: JsonObject): String {
        val songId = args["songId"]?.jsonPrimitive?.content ?: "?"
        val lyrics = args["lyrics"]?.jsonPrimitive?.content.orEmpty()
        return "Set lyrics (${lyrics.length} chars) for song $songId"
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val songId = args["songId"]?.jsonPrimitive?.content
            ?: return ToolOutcome("Missing songId", isError = true)
        val lyrics = args["lyrics"]?.jsonPrimitive?.content
            ?: return ToolOutcome("Missing lyrics", isError = true)
        val song = songRepository.getSongById(songId)
            ?: return ToolOutcome("Unknown song id: $songId", isError = true)
        return updateMetadata(song, lyrics = lyrics, writeLyricsToTag = true)
            .fold(
                onSuccess = {
                    scanFiles(appContext, listOf(song.path))
                    ToolOutcome("Lyrics written for \"${song.title}\"")
                },
                onFailure = {
                    ToolOutcome(it.message ?: "Failed to write lyrics", isError = true)
                }
            )
    }
}

/**
 * `set_rating_favorite` — instant DB-only rating/favorite update.
 */
class SetRatingFavoriteTool(
    private val songRepository: SongRepository
) : AiTool {

    override val name = "set_rating_favorite"
    override val description = "Set the star rating (0-5) and/or favorite flag of a song. " +
        "Database-only, applies instantly."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("songId", buildJsonObject { put("type", "string") })
            put("rating", buildJsonObject {
                put("type", "number")
                put("minimum", 0)
                put("maximum", 5)
            })
            put("favorite", buildJsonObject { put("type", "boolean") })
        }
        putJsonArray("required") { add(JsonPrimitive("songId")) }
    }
    override val requiredGrants = setOf(AiCapability.WRITE_METADATA)
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override fun summarize(args: JsonObject): String {
        val songId = args["songId"]?.jsonPrimitive?.content ?: "?"
        return "Update rating/favorite of song $songId"
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val songId = args["songId"]?.jsonPrimitive?.content
            ?: return ToolOutcome("Missing songId", isError = true)
        val song = songRepository.getSongById(songId)
            ?: return ToolOutcome("Unknown song id: $songId", isError = true)
        args["rating"]?.jsonPrimitive?.content?.toFloatOrNull()?.let {
            songRepository.updateSongRating(song.id, it.coerceIn(0f, 5f))
        }
        args["favorite"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()?.let {
            songRepository.updateSongFavorite(song.id, it)
        }
        return ToolOutcome("Updated \"${song.title}\"")
    }
}

/**
 * `embed_artwork` — downloads an image and embeds it into the MP3.
 * (iTunes/search sources arrive with the internet tools.)
 */
class EmbedArtworkTool(
    private val appContext: android.content.Context,
    private val songRepository: SongRepository,
    private val writeArtwork: WriteArtworkToMp3UseCase,
    private val coverCache: CoverCache,
    private val downloadStore: com.synth.synthmusic.data.ai.tools.DownloadStore
) : AiTool {

    override val name = "embed_artwork"
    override val description = "Download an image from imageUrl (http/https, max 8 MB) and " +
        "embed it as the song's artwork. Updates the cover cache and MediaStore."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("songId", buildJsonObject { put("type", "string") })
            put("imageUrl", buildJsonObject { put("type", "string") })
            put("attachmentId", buildJsonObject {
                put("type", "string")
                put("description", "Id from download_image; alternative to imageUrl")
            })
        }
        putJsonArray("required") { add(JsonPrimitive("songId")) }
    }
    override val requiredGrants = setOf(AiCapability.WRITE_METADATA, AiCapability.INTERNET)
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override fun summarize(args: JsonObject): String {
        val songId = args["songId"]?.jsonPrimitive?.content ?: "?"
        val url = args["imageUrl"]?.jsonPrimitive?.content ?: "?"
        return "Embed artwork from $url into song $songId"
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val songId = args["songId"]?.jsonPrimitive?.content
            ?: return ToolOutcome("Missing songId", isError = true)
        val url = args["imageUrl"]?.jsonPrimitive?.content
        val attachmentId = args["attachmentId"]?.jsonPrimitive?.content
        val song = songRepository.getSongById(songId)
            ?: return ToolOutcome("Unknown song id: $songId", isError = true)
        val resolveResult: Result<ByteArray> = when {
            !url.isNullOrBlank() -> runCatching {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    error("Only http/https image URLs are allowed")
                }
                val connection = URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 20_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = true
                val contentType = connection.contentType.orEmpty()
                val bytes = connection.inputStream.use { it.readBytes() }
                connection.disconnect()
                if (!contentType.startsWith("image/")) {
                    error("URL did not return an image (content-type: $contentType)")
                }
                if (bytes.size > MAX_IMAGE_BYTES) {
                    error("Image exceeds the 8 MB size limit")
                }
                bytes
            }
            !attachmentId.isNullOrBlank() -> runCatching {
                downloadStore.resolve(attachmentId)?.readBytes()
                    ?: error("Unknown attachmentId: $attachmentId")
            }
            else -> Result.failure(IllegalArgumentException("Provide imageUrl or attachmentId"))
        }
        return resolveResult.fold(
            onSuccess = { bytes ->
                writeArtwork(song, bytes).fold(
                    onSuccess = {
                        coverCache.saveSongArtwork(song.id, bytes)
                        songRepository.updateSongArtwork(
                            song.id,
                            songRepository.getSongById(song.id)?.artworkUri
                        )
                        scanFiles(appContext, listOf(song.path))
                        ToolOutcome("Artwork embedded into \"${song.title}\"")
                    },
                    onFailure = {
                        ToolOutcome(it.message ?: "Embedding failed", isError = true)
                    }
                )
            },
            onFailure = {
                ToolOutcome(it.message ?: "Download failed", isError = true)
            }
        )
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    }
}

/**
 * `remove_artwork` — removes the embedded cover from a song.
 */
class RemoveArtworkTool(
    private val appContext: android.content.Context,
    private val songRepository: SongRepository,
    private val writeArtwork: WriteArtworkToMp3UseCase
) : AiTool {

    override val name = "remove_artwork"
    override val description = "Remove the embedded artwork from one song's MP3 file."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("songId", buildJsonObject { put("type", "string") })
        }
        putJsonArray("required") { add(JsonPrimitive("songId")) }
    }
    override val requiredGrants = setOf(AiCapability.WRITE_METADATA)
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override fun summarize(args: JsonObject): String {
        val songId = args["songId"]?.jsonPrimitive?.content ?: "?"
        return "Remove artwork from song $songId"
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val songId = args["songId"]?.jsonPrimitive?.content
            ?: return ToolOutcome("Missing songId", isError = true)
        val song = songRepository.getSongById(songId)
            ?: return ToolOutcome("Unknown song id: $songId", isError = true)
        return writeArtwork.removeArtwork(song).fold(
            onSuccess = {
                scanFiles(appContext, listOf(song.path))
                ToolOutcome("Artwork removed from \"${song.title}\"")
            },
            onFailure = {
                ToolOutcome(it.message ?: "Removal failed", isError = true)
            }
        )
    }
}

/**
 * `manage_playlist` — create/rename playlists, add/remove songs.
 */
class ManagePlaylistTool(
    private val playlistRepository: PlaylistRepository
) : AiTool {

    override val name = "manage_playlist"
    override val description = "Playlist management: create (name), rename (playlistId, name), " +
        "add songs (playlistId, songIds), remove songs (playlistId, songIds)."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    listOf("create", "rename", "add_songs", "remove_songs").forEach {
                        add(JsonPrimitive(it))
                    }
                }
            }
            put("playlistId", buildJsonObject { put("type", "integer") })
            put("name", buildJsonObject { put("type", "string") })
            put("songIds", buildJsonObject {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
            })
        }
        putJsonArray("required") { add(JsonPrimitive("action")) }
    }
    override val requiredGrants = setOf(AiCapability.WRITE_METADATA)
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override fun summarize(args: JsonObject): String {
        val action = args["action"]?.jsonPrimitive?.content ?: "?"
        return "Playlist action: $action ${args}"
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val action = args["action"]?.jsonPrimitive?.content
            ?: return ToolOutcome("Missing action", isError = true)
        return when (action) {
            "create" -> {
                val name = args["name"]?.jsonPrimitive?.content
                    ?: return ToolOutcome("Missing name", isError = true)
                val id = playlistRepository.createPlaylist(name)
                ToolOutcome("Created playlist \"$name\" (id=$id)")
            }
            "rename" -> {
                val id = args["playlistId"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: return ToolOutcome("Missing playlistId", isError = true)
                val name = args["name"]?.jsonPrimitive?.content
                    ?: return ToolOutcome("Missing name", isError = true)
                playlistRepository.renamePlaylist(id, name)
                ToolOutcome("Renamed playlist to \"$name\"")
            }
            "add_songs", "remove_songs" -> {
                val id = args["playlistId"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: return ToolOutcome("Missing playlistId", isError = true)
                val songIds = stringArray(args, "songIds").take(MAX_FILES_PER_CALL)
                var done = 0
                songIds.forEach { songId ->
                    if (action == "add_songs") {
                        playlistRepository.addSongToPlaylist(id, songId)
                    } else {
                        playlistRepository.removeSongFromPlaylist(id, songId)
                    }
                    done++
                }
                ToolOutcome("${if (action == "add_songs") "Added" else "Removed"} $done song(s)")
            }
            else -> ToolOutcome("Unknown action: $action", isError = true)
        }
    }
}

/**
 * Fires a MediaStore rescan for the given paths and waits (bounded) for the
 * scanner to finish, so a subsequent library scan reads up-to-date metadata
 * instead of stale pre-edit MediaStore rows.
 */
internal suspend fun scanFiles(context: android.content.Context, paths: List<String>) {
    if (paths.isEmpty()) return
    runCatching {
        withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(
                    context,
                    paths.toTypedArray(),
                    arrayOf("audio/mpeg")
                ) { _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }
}

private const val SCAN_TIMEOUT_MS = 30_000L
