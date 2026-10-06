package com.synth.synthmusic.data.ai.tools

import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.usecase.ai.AiTool
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File

/**
 * Reason codes reported by [auditIssues] / [deepTagIssues] for missing
 * metadata. The codes are part of the `audit_tracks` tool contract — the
 * model filters on them, so keep them stable.
 */
internal object AuditReasons {
    const val NO_TITLE = "no_title"
    const val NO_ARTIST = "no_artist"
    const val NO_ALBUM = "no_album"
    const val NO_GENRE = "no_genre"
    const val NO_YEAR = "no_year"
    const val NO_TRACK_NUMBER = "no_track_number"
    const val NO_LYRICS = "no_lyrics"
    const val NO_COVER = "no_cover"
    const val NO_DURATION = "no_duration"
    const val UNREADABLE_FILE = "unreadable_file"
    const val BLACKLISTED_COVER = "blacklisted_cover"

    /**
     * The fields whose absence can be verified against the real ID3 tag;
     * in deep mode the file-level verdict replaces the database-level one.
     */
    val FILE_VERIFIABLE = setOf(
        NO_TITLE, NO_ARTIST, NO_ALBUM, NO_GENRE, NO_YEAR, NO_TRACK_NUMBER, NO_COVER
    )
}

/**
 * Values that MediaStore or broken ID3 tags leave in place of real data.
 * Case-insensitive; anything else counts as present.
 */
internal fun isMissingMetadata(value: String?): Boolean {
    val v = value?.trim()?.lowercase().orEmpty()
    return v.isEmpty() || v in setOf(
        "<unknown>", "unknown", "unknown artist", "unknown title", "unknown album"
    )
}

/**
 * Database-level audit of a song's indexed metadata (no file access).
 */
internal fun auditIssues(song: Song): List<String> = buildList {
    if (isMissingMetadata(song.title)) add(AuditReasons.NO_TITLE)
    if (isMissingMetadata(song.artist)) add(AuditReasons.NO_ARTIST)
    if (isMissingMetadata(song.album)) add(AuditReasons.NO_ALBUM)
    if (song.genre.isBlank()) add(AuditReasons.NO_GENRE)
    if (song.year <= 0) add(AuditReasons.NO_YEAR)
    if (song.trackNumber <= 0) add(AuditReasons.NO_TRACK_NUMBER)
    if (song.lyrics.isNullOrBlank()) add(AuditReasons.NO_LYRICS)
    if (song.artworkUri.isNullOrBlank()) add(AuditReasons.NO_COVER)
    if (song.durationMs <= 0L) add(AuditReasons.NO_DURATION)
}

/**
 * File-level audit via JAudioTagger: what the real ID3 tags are missing.
 * Returns [AuditReasons.UNREADABLE_FILE] alone when the file cannot be read.
 * Only contains [AuditReasons.FILE_VERIFIABLE] codes.
 */
internal suspend fun deepFileIssues(song: Song): List<String> = withContext(Dispatchers.IO) {
    runCatching { tagIssues(AudioFileIO.read(File(song.path)).tag) }
        .getOrDefault(listOf(AuditReasons.UNREADABLE_FILE))
}

/**
 * Tag-level audit shared by [deepFileIssues]; takes the parsed ID3 tag
 * (null when the file has none) and reports missing [AuditReasons.FILE_VERIFIABLE]
 * codes.
 */
internal fun tagIssues(tag: Tag?): List<String> = buildList {
    fun missing(key: FieldKey) = tag == null ||
        isMissingMetadata(runCatching { tag.getFirst(key) }.getOrNull())
    if (missing(FieldKey.TITLE)) add(AuditReasons.NO_TITLE)
    if (missing(FieldKey.ARTIST)) add(AuditReasons.NO_ARTIST)
    if (missing(FieldKey.ALBUM)) add(AuditReasons.NO_ALBUM)
    if (missing(FieldKey.GENRE)) add(AuditReasons.NO_GENRE)
    if (tag == null ||
        tag.getFirst(FieldKey.YEAR).trim().toIntOrNull()?.let { it > 0 } != true
    ) add(AuditReasons.NO_YEAR)
    if (tag == null ||
        tag.getFirst(FieldKey.TRACK).trim().toIntOrNull()?.let { it > 0 } != true
    ) add(AuditReasons.NO_TRACK_NUMBER)
    if (tag == null || tag.firstArtwork == null) add(AuditReasons.NO_COVER)
}

/**
 * `audit_tracks` — find tracks with missing metadata within a scope
 * (whole library or one playlist), each with stable reason codes such as
 * `no_cover` / `no_artist`. The model can then inspect candidates via
 * `get_songs_details` and fix them with `update_songs_metadata`.
 */
class AuditTracksTool(
    private val allSongs: suspend () -> List<Song>,
    private val playlistSongs: suspend (Long) -> List<Song>,
    private val deepFileIssues: suspend (Song) -> List<String> = ::deepFileIssues,
    private val embeddedCoverHash: suspend (Song) -> String? = { null },
    private val isCoverBlacklisted: suspend (String) -> Boolean = { false }
) : AiTool {

    override val name = "audit_tracks"
    override val description = "Audit metadata completeness in a scope (whole library or " +
        "one playlist; playlist ids come from browse_collections). Returns one entry per " +
        "problematic track: id, title, artist, album (empty string when missing) and the " +
        "list of issues. Issue codes: no_title, no_artist, no_album, no_genre, no_year, " +
        "no_track_number, no_lyrics, no_cover, no_duration, blacklisted_cover (deep only, " +
        "image is on the manage_cover_blacklist list) and unreadable_file (deep only). " +
        "Set deep=true to read the real ID3 tags from files instead of the library index " +
        "(slower, catches file-vs-index divergence). Filter with `issues` to target one " +
        "problem, e.g. only no_cover. Typical flow: audit_tracks -> get_songs_details -> " +
        "update_songs_metadata; blacklisted covers are cleaned with purge_covers."
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
            putJsonObject("issues") {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
                put("description", "Optional filter: only return tracks having at least " +
                    "one of these issue codes")
            }
            putJsonObject("deep") {
                put("type", "boolean")
                put("description", "Read real ID3 tags from files (default false)")
            }
            put("limit", ToolSchemas.intProperty("Max tracks to return", 1, MAX_LIMIT))
        }
        putJsonArray("required") { add(JsonPrimitive("scope")) }
    }
    override val requiredGrants = setOf(AiCapability.READ_LIBRARY)

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
        val deep = args["deep"]?.jsonPrimitive?.content == "true"
        val issueFilter = (args["issues"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?.toSet()
            .orEmpty()
        val limit = (args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: DEFAULT_LIMIT)
            .coerceIn(1, MAX_LIMIT)

        // One audit pass per song, reused for both the counts and the track list.
        val issuesBySong = songs.map { it to baseIssues(it, deep) }
        val audited = issuesBySong.mapNotNull { (song, allIssues) ->
            val issues = if (issueFilter.isEmpty()) {
                allIssues
            } else {
                allIssues.filter { it in issueFilter }
            }
            if (issues.isEmpty()) null else song to issues
        }.sortedWith(compareByDescending<Pair<Song, List<String>>> { it.second.size }
            .thenBy { it.first.title.lowercase() })

        val counts = issuesBySong
            .flatMap { (_, issues) -> issues }
            .groupingBy { it }
            .eachCount()

        val truncated = audited.size > limit
        return ToolOutcome(
            buildJsonObject {
                put("scope", scope)
                if (scope == "playlist") {
                    args["playlistId"]?.let { put("playlist_id", it.jsonPrimitive.content) }
                }
                put("checked", songs.size)
                put("problematic", audited.size)
                putJsonObject("issue_counts") {
                    counts.toSortedMap().forEach { (code, n) -> put(code, n) }
                }
                if (truncated) {
                    put("truncated", true)
                    put("hint", "Raise limit (max $MAX_LIMIT) or narrow with the issues filter")
                }
                put(
                    "tracks",
                    buildJsonArray {
                        audited.take(limit).forEach { (song, issues) ->
                            add(
                                buildJsonObject {
                                    put("id", song.id)
                                    put("title", song.title.takeUnless { isMissingMetadata(it) } ?: "")
                                    put("artist", song.artist.takeUnless { isMissingMetadata(it) } ?: "")
                                    put("album", song.album.takeUnless { isMissingMetadata(it) } ?: "")
                                    putJsonArray("issues") { issues.forEach { add(JsonPrimitive(it)) } }
                                }
                            )
                        }
                    }
                )
            }.toString()
        )
    }

    /** Issues for one song; deep mode replaces file-verifiable codes with tag verdicts. */
    private suspend fun baseIssues(song: Song, deep: Boolean): List<String> {
        val dbIssues = auditIssues(song)
        if (!deep) return dbIssues
        val fileIssues = deepFileIssues(song)
        val merged = if (AuditReasons.UNREADABLE_FILE in fileIssues) {
            // Nothing reliable from the file: keep the index verdict and flag it.
            dbIssues + fileIssues
        } else {
            dbIssues.filter { it !in AuditReasons.FILE_VERIFIABLE } + fileIssues
        }
        if (AuditReasons.UNREADABLE_FILE in merged) return merged
        val hash = embeddedCoverHash(song)
        if (hash != null && isCoverBlacklisted(hash)) {
            return merged + AuditReasons.BLACKLISTED_COVER
        }
        return merged
    }

    private companion object {
        const val MAX_LIMIT = 500
        const val DEFAULT_LIMIT = 200
    }
}
