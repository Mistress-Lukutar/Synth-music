package com.synth.synthmusic.data.ai.tools

import android.content.Context
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.CheckWritePermissionUseCase
import com.synth.synthmusic.domain.usecase.ScanMusicUseCase
import com.synth.synthmusic.domain.usecase.ai.AiTool
import com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.net.URLDecoder

/** Hard cap for a single downloaded audio file (streamed to disk, never held in RAM). */
internal const val MAX_AUDIO_BYTES = 200L * 1024 * 1024

/** Characters stripped from model-supplied file and folder names (mirrors FileTools). */
internal const val ILLEGAL_NAME_CHARS = ":*?\"<>|"

/** Maximum depth of a relative target folder under the Music root. */
internal const val MAX_FOLDER_DEPTH = 8

/** Maximum length of a single file name segment. */
internal const val MAX_NAME_LENGTH = 120

internal const val DOWNLOAD_BUFFER_BYTES = 64 * 1024

/**
 * MP3 signature check over the first bytes of a download: true for an ID3v2
 * tag ("ID3") or an MPEG audio frame sync with valid version / layer /
 * bitrate / sample-rate bits (reserved bit patterns are rejected).
 */
internal fun looksLikeMp3(head: ByteArray, length: Int = head.size): Boolean {
    if (length >= 3 &&
        head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()
    ) {
        return true
    }
    if (length < 2 || (head[0].toInt() and 0xFF) != 0xFF) return false
    val b = head[1].toInt() and 0xFF
    if (b and 0xE0 != 0xE0) return false          // frame sync bits
    if (b and 0x18 == 0x08) return false          // 01 = reserved MPEG version
    if (b and 0x06 == 0x00) return false          // 00 = reserved layer
    if (length >= 3) {
        val c = head[2].toInt() and 0xFF
        if (c and 0xF0 == 0xF0) return false      // 1111 = invalid bitrate index
        if (c and 0x0C == 0x0C) return false      // 11 = reserved sample rate
    }
    return true
}

/**
 * Normalizes a model-supplied relative folder ("Rock", "Archive/2024") into
 * validated segments: empty segments are dropped, illegal filename characters
 * are stripped, "." / ".." / all-dot segments are rejected. Returns null when
 * nothing valid remains or the path is deeper than [MAX_FOLDER_DEPTH].
 */
internal fun normalizeRelativeFolder(raw: String): List<String>? {
    if (raw.isBlank()) return null
    val segments = raw.split('/', '\\')
        .map { segment ->
            segment.filter { it !in ILLEGAL_NAME_CHARS && it !in "/\\" }
                .trim()
                .take(MAX_NAME_LENGTH)
        }
        .filter { it.isNotEmpty() }
    if (segments.isEmpty() || segments.size > MAX_FOLDER_DEPTH) return null
    if (segments.any { segment -> segment.all { it == '.' } }) return null
    return segments
}

/**
 * Resolves the target file name: [explicit] wins; otherwise the URL's last
 * path segment (query stripped, percent-decoded). Illegal characters are
 * stripped and the .mp3 extension is appended when missing.
 */
internal fun suggestFileName(explicit: String?, url: String): String {
    val rawName = if (explicit.isNullOrBlank()) {
        runCatching {
            URLDecoder.decode(url.substringBefore('?').substringAfterLast('/'), "UTF-8")
        }.getOrNull().orEmpty().ifBlank { "download" }
    } else {
        explicit
    }
    val sanitized = rawName.filter { it !in ILLEGAL_NAME_CHARS && it !in "/\\" }
        .trim()
        .take(MAX_NAME_LENGTH)
    if (sanitized.isBlank() || sanitized.equals(".mp3", ignoreCase = true)) {
        return "download.mp3"
    }
    return if (sanitized.endsWith(".mp3", ignoreCase = true)) sanitized else "$sanitized.mp3"
}

/**
 * Returns a non-existing file inside [dir], uniquifying "Name.mp3" as
 * "Name (1).mp3", "Name (2).mp3", … so batch downloads never overwrite.
 */
internal fun uniqueTargetFile(dir: File, fileName: String): File {
    val base = fileName.removeSuffix(".mp3")
    var candidate = File(dir, fileName)
    var index = 1
    while (candidate.exists()) {
        candidate = File(dir, "$base ($index).mp3")
        index++
    }
    return candidate
}

/**
 * `download_audio` — fetches an MP3 from a direct http(s) URL into the device
 * music storage (folders under the public Music root are created as needed),
 * fires a MediaStore scan and refreshes the library, returning the new song
 * id so follow-up tools can reference the track immediately.
 *
 * The pipeline lives in [AudioFileDownloader] so custom scripted tools can
 * offer the exact same behavior via `ctx.downloadAudio()`.
 */
class DownloadAudioTool(
    appContext: Context,
    songRepository: SongRepository,
    scanMusicUseCase: ScanMusicUseCase,
    checkWritePermission: CheckWritePermissionUseCase
) : AiTool {

    private val downloader = AudioFileDownloader(
        appContext, songRepository, scanMusicUseCase, checkWritePermission
    )

    override val name = "download_audio"
    override val description = "Download an MP3 from a direct http(s) URL into the device music " +
        "storage and refresh the library; the result includes the new songId. " +
        "folder is relative to the public Music directory ('Rock', 'Archive/2024') and missing " +
        "folders are created; an absolute folder path is only accepted when it is an existing " +
        "music folder from list_music_folders. Without folder the file lands in the Music root. " +
        "MP3 only (ID3 / MPEG audio signature check), max 200 MB."

    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("url", ToolSchemas.queryProperty("Direct http(s) URL of the MP3 file"))
            put(
                "folder",
                ToolSchemas.queryProperty(
                    "Optional target folder: relative to the Music root (created as needed) " +
                        "or an absolute path from list_music_folders"
                )
            )
            put(
                "fileName",
                ToolSchemas.queryProperty(
                    "Optional file name without directory, e.g. 'Artist - Title.mp3'"
                )
            )
        }
        putJsonArray("required") { add(JsonPrimitive("url")) }
    }
    override val requiredGrants = setOf(AiCapability.INTERNET, AiCapability.MANAGE_FILES)
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override fun summarize(args: JsonObject): String {
        val url = args["url"]?.jsonPrimitive?.content ?: "?"
        val folder = args["folder"]?.jsonPrimitive?.content
        val name = suggestFileName(args["fileName"]?.jsonPrimitive?.content, url)
        val target = if (folder.isNullOrBlank()) name else "$folder/$name"
        return "Download MP3 → $target"
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome =
        withContext(Dispatchers.IO) {
            val url = args["url"]?.jsonPrimitive?.content
                ?: return@withContext ToolOutcome("Missing url", isError = true)
            downloader.download(
                url = url,
                folder = args["folder"]?.jsonPrimitive?.content,
                fileName = args["fileName"]?.jsonPrimitive?.content
            )
        }
}

/**
 * `refresh_library` — reruns the incremental storage scan that the app already
 * performs at startup: new files are indexed, changed files re-read, rows for
 * deleted files pruned. Useful after files were added, moved or removed
 * outside the app; cheap for an unchanged library.
 */
class RefreshLibraryTool(
    private val scanMusicUseCase: ScanMusicUseCase
) : AiTool {

    override val name = "refresh_library"
    override val description = "Rescan device storage and rebuild the library index " +
        "(incremental: only new or changed files are re-read, so it is fast). Use it after " +
        "audio files were added, moved, renamed or deleted outside the app. download_audio " +
        "already refreshes the library by itself."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { }
    }
    override val requiredGrants = setOf(AiCapability.READ_LIBRARY)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        return scanMusicUseCase().fold(
            onSuccess = { count -> ToolOutcome("Library refreshed: $count songs indexed") },
            onFailure = { e ->
                ToolOutcome("Scan failed: ${e.message ?: "unknown error"}", isError = true)
            }
        )
    }
}
