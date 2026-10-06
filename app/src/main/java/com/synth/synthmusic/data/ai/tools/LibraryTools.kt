package com.synth.synthmusic.data.ai.tools

import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.usecase.ai.AiTool
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Compact JSON summary of a song used inside tool results: ids + short
 * fields only; full details live behind `get_songs_details`.
 */
fun songSummary(song: Song): JsonObject = buildJsonObject {
    put("id", song.id)
    put("title", song.title)
    put("artist", song.artist)
    put("album", song.album)
    put("duration_ms", song.durationMs)
}

/**
 * Shared schema helpers for library tools.
 */
object ToolSchemas {

    /** JSON schema for an optional string query filter. */
    fun queryProperty(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    /** JSON schema for an integer parameter with bounds. */
    fun intProperty(description: String, minimum: Int, maximum: Int): JsonObject =
        buildJsonObject {
            put("type", "integer")
            put("description", description)
            put("minimum", minimum)
            put("maximum", maximum)
        }
}

/**
 * `search_songs` — fuzzy, punctuation-tolerant match across title/artist/album.
 */
class SearchSongsTool(
    private val allSongs: suspend () -> List<Song>
) : AiTool {

    override val name = "search_songs"
    override val description = "Search the music library for songs matching a text query " +
        "across title, artist and album. Tolerates filename-style punctuation " +
        "(\"rush e\" finds \"RUSH_E\") and small typos; results are ranked by similarity. " +
        "Returns compact results (id, title, artist, album, duration). " +
        "Use get_songs_details for full metadata of specific songs."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("query", ToolSchemas.queryProperty("Text to search for (required)"))
            put("limit", ToolSchemas.intProperty("Max results to return", 1, MAX_LIMIT))
        }
        putJsonArray("required") { add(JsonPrimitive("query")) }
    }
    override val requiredGrants = setOf(AiCapability.READ_LIBRARY)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val query = args["query"]?.toString()?.trim('"') ?: ""
        if (query.isEmpty()) {
            return ToolOutcome("Missing required parameter: query", isError = true)
        }
        val limit = (args["limit"]?.toString()?.toIntOrNull() ?: DEFAULT_LIMIT)
            .coerceIn(1, MAX_LIMIT)
        val songs = allSongs()
        val matcher = SongSearchMatcher(query)
        // Strict pass: one field covers every query token. Only when nothing
        // matches, fall back to the relaxed pass and flag partial matches so
        // the model knows results may be approximations.
        val strict = songs.mapNotNull { song ->
            matcher.strictScore(song.title, song.artist, song.album)?.let { song to it }
        }
        val partial = strict.isEmpty()
        val results = (if (partial) {
            songs.mapNotNull { song ->
                matcher.partialScore(song.title, song.artist, song.album)?.let { song to it }
            }
        } else {
            strict
        }).sortedByDescending { it.second }.take(limit)
        return ToolOutcome(
            buildJsonObject {
                put("count", results.size)
                if (partial) put("partial_match", true)
                put("songs", buildJsonArray { results.forEach { add(songSummary(it.first)) } })
            }.toString()
        )
    }

    private companion object {
        const val MAX_LIMIT = 50
        const val DEFAULT_LIMIT = 20
    }
}

/**
 * `get_songs_details` — full metadata for specific song ids.
 */
class GetSongsDetailsTool(
    private val getSongsByIds: suspend (List<String>) -> List<Song>
) : AiTool {

    override val name = "get_songs_details"
    override val description = "Return full metadata (genre, year, track, rating, favorite, " +
        "lyrics, file path, size) for the given song ids. Use after search_songs."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("songIds") {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
                put("description", "Song ids to inspect (max 50)")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("songIds")) }
    }
    override val requiredGrants = setOf(AiCapability.READ_LIBRARY)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val ids = (args["songIds"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            .orEmpty()
        if (ids.isEmpty()) {
            return ToolOutcome("Missing required parameter: songIds", isError = true)
        }
        val songs = getSongsByIds(ids.take(MAX_IDS))
        return ToolOutcome(
            buildJsonObject {
                put("count", songs.size)
                put("songs", buildJsonArray { songs.forEach { add(songDetails(it)) } })
            }.toString()
        )
    }

    private fun songDetails(song: Song): JsonObject = buildJsonObject {
        put("id", song.id)
        put("title", song.title)
        put("artist", song.artist)
        put("album", song.album)
        put("album_artist", song.albumArtist)
        put("genre", song.genre)
        put("year", song.year)
        put("track", song.trackNumber)
        put("duration_ms", song.durationMs)
        put("rating", song.rating.toDouble())
        put("favorite", song.isFavorite)
        put("has_lyrics", !song.lyrics.isNullOrBlank())
        song.lyrics?.let { put("lyrics", it.take(LYRICS_PREVIEW_CHARS)) }
        put("file_path", song.path)
        put("file_size", song.fileSize)
        put("format", song.path.substringAfterLast('.', ""))
    }

    private companion object {
        const val MAX_IDS = 50
        const val LYRICS_PREVIEW_CHARS = 2000
    }
}

/**
 * `browse_collections` — albums/artists/genres/playlists with song counts.
 */
class BrowseCollectionsTool(
    private val allSongs: suspend () -> List<Song>,
    private val genres: suspend () -> List<String>,
    private val playlists: suspend () -> List<Triple<Long, String, Int>>
) : AiTool {

    override val name = "browse_collections"
    override val description = "List collections: albums, artists, genres or playlists, " +
        "each with song counts. Optionally filter by a text query."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("type") {
                put("type", "string")
                putJsonArray("enum") {
                    listOf("albums", "artists", "genres", "playlists").forEach {
                        add(JsonPrimitive(it))
                    }
                }
                put("description", "Which collection type to browse")
            }
            put("query", ToolSchemas.queryProperty("Optional name filter"))
        }
        putJsonArray("required") { add(JsonPrimitive("type")) }
    }
    override val requiredGrants = setOf(AiCapability.READ_LIBRARY)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val type = args["type"]?.toString()?.trim('"') ?: ""
        val query = args["query"]?.toString()?.trim('"') ?: ""
        val entries = when (type) {
            "albums" -> allSongs()
                .groupBy { it.album }
                .map { (album, songs) ->
                    Triple(album, songs.size, songs.firstOrNull()?.albumArtist.orEmpty())
                }
            "artists" -> allSongs()
                .groupBy { it.artist }
                .map { (artist, songs) -> Triple(artist, songs.size, "") }
            "genres" -> genres()
                .map { genre ->
                    val count = allSongs().count { it.genre.equals(genre, ignoreCase = true) }
                    Triple(genre, count, "")
                }
            "playlists" -> playlists().map { Triple(it.second, it.third, it.first.toString()) }
            else -> return ToolOutcome(
                "Unknown type: $type (use albums, artists, genres or playlists)",
                isError = true
            )
        }
        val filtered = if (query.isEmpty()) entries else entries
            .filter { it.first.contains(query, ignoreCase = true) }
        return ToolOutcome(
            buildJsonObject {
                put("type", type)
                put("count", filtered.size)
                put(
                    "items",
                    buildJsonArray {
                        filtered.take(MAX_ENTRIES).forEach { entry ->
                            add(
                                buildJsonObject {
                                    put("name", entry.first)
                                    put("song_count", entry.second)
                                }
                            )
                        }
                    }
                )
            }.toString()
        )
    }

    private companion object {
        const val MAX_ENTRIES = 60
    }
}

/**
 * `get_playlist` — songs of a playlist in order.
 */
class GetPlaylistTool(
    private val playlistSongs: suspend (Long) -> List<Song>
) : AiTool {

    override val name = "get_playlist"
    override val description = "Return the songs of a playlist in order, given its playlist id " +
        "(ids come from browse_collections with type=playlists)."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("playlistId") {
                put("type", "integer")
                put("description", "Playlist id")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("playlistId")) }
    }
    override val requiredGrants = setOf(AiCapability.READ_LIBRARY)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val playlistId = args["playlistId"]?.toString()?.toLongOrNull()
            ?: return ToolOutcome("Missing or invalid playlistId", isError = true)
        val songs = playlistSongs(playlistId)
        return ToolOutcome(
            buildJsonObject {
                put("playlist_id", playlistId)
                put("count", songs.size)
                put("songs", buildJsonArray { songs.forEach { add(songSummary(it)) } })
            }.toString()
        )
    }
}

/**
 * `library_stats` — counts and metadata completeness ("cleanup report").
 */
class LibraryStatsTool(
    private val allSongs: suspend () -> List<Song>
) : AiTool {

    override val name = "library_stats"
    override val description = "Library statistics: song/album/artist counts, total duration " +
        "and the percentage of songs missing artwork, genre, year or lyrics. Useful to plan " +
        "a metadata cleanup."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { }
    }
    override val requiredGrants = setOf(AiCapability.READ_LIBRARY)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val songs = allSongs()
        val total = songs.size
        fun pct(missing: Int): Double =
            if (total == 0) 0.0 else missing * 100.0 / total
        return ToolOutcome(
            buildJsonObject {
                put("songs", total)
                put("albums", songs.map { it.album }.distinct().size)
                put("artists", songs.map { it.artist }.distinct().size)
                put("total_duration_ms", songs.sumOf { it.durationMs })
                put("missing_artwork_pct", pct(songs.count { it.artworkUri.isNullOrBlank() }))
                put("missing_genre_pct", pct(songs.count { it.genre.isBlank() }))
                put("missing_year_pct", pct(songs.count { it.year <= 0 }))
                put("missing_lyrics_pct", pct(songs.count { it.lyrics.isNullOrBlank() }))
            }.toString()
        )
    }
}

/**
 * `get_playback_state` — what the user is hearing right now (read-only).
 */
class GetPlaybackStateTool(
    private val playbackSnapshot: () -> Triple<String?, Boolean, Boolean>,
    private val getSongById: suspend (String) -> Song?
) : AiTool {

    override val name = "get_playback_state"
    override val description = "Return the current playback state: the now-playing song " +
        "(id, title, artist, album, duration), playing/paused and shuffle state. " +
        "Use this instead of search_songs whenever the user refers to the current " +
        "or now-playing track."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { }
    }
    override val requiredGrants = setOf(AiCapability.READ_LIBRARY)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val (songId, isPlaying, shuffle) = playbackSnapshot()
        val song = songId?.let { getSongById(it) }
        return ToolOutcome(
            buildJsonObject {
                put("current_song_id", songId)
                song?.let { put("song", songSummary(it)) }
                put("is_playing", isPlaying)
                put("shuffle", shuffle)
            }.toString()
        )
    }
}
