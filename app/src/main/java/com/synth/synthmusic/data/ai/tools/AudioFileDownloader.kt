package com.synth.synthmusic.data.ai.tools

import android.content.Context
import android.os.Environment
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.CheckWritePermissionUseCase
import com.synth.synthmusic.domain.usecase.ScanMusicUseCase
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Shared MP3 download pipeline used by both the built-in `download_audio`
 * tool and the `ctx.downloadAudio()` host API of custom scripted tools:
 * resolves the target folder under the public Music root, streams the
 * response into a temp file with signature and size validation, finalizes
 * the file, then scans it into MediaStore and refreshes the library.
 */
class AudioFileDownloader(
    private val appContext: Context,
    private val songRepository: SongRepository,
    private val scanMusicUseCase: ScanMusicUseCase,
    private val checkWritePermission: CheckWritePermissionUseCase
) {

    /**
     * Downloads [url] as an MP3. [folder] is relative to the Music root (or
     * an absolute existing music folder), [fileName] overrides the URL-
     * derived name, [headers] are appended after the default User-Agent.
     * Failures — permission, policy, network, validation — are returned as
     * error outcomes, never thrown.
     */
    suspend fun download(
        url: String,
        folder: String?,
        fileName: String?,
        headers: Map<String, String> = emptyMap()
    ): ToolOutcome = withContext(Dispatchers.IO) {
        if (!checkWritePermission()) {
            return@withContext ToolOutcome(
                "No storage write permission; the user must grant \"All files access\" " +
                    "for Synth Music in system settings",
                isError = true
            )
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext ToolOutcome("Only http/https URLs are allowed", isError = true)
        }
        if (isBlockedHost(url)) {
            return@withContext ToolOutcome("Blocked host", isError = true)
        }
        val targetDir = resolveTargetDir(folder)
            ?: return@withContext ToolOutcome(
                "Invalid folder: use a relative path under the Music root " +
                    "(e.g. 'Rock' or 'Archive/2024') or an existing folder from " +
                    "list_music_folders",
                isError = true
            )
        if (!targetDir.isDirectory && !targetDir.mkdirs()) {
            return@withContext ToolOutcome(
                "Could not create folder ${targetDir.absolutePath}", isError = true
            )
        }
        val target = uniqueTargetFile(targetDir, suggestFileName(fileName, url))

        val download = downloadToTemp(url, target, headers)
        if (download.isError) return@withContext download

        scanFiles(appContext, listOf(target.absolutePath))
        val refreshed = scanMusicUseCase().getOrNull()
        val song = songRepository.getAllSongs().firstOrNull { it.path == target.absolutePath }
        ToolOutcome(
            buildJsonObject {
                put("savedAs", target.name)
                put("folder", target.parentFile?.absolutePath.orEmpty())
                put("sizeBytes", target.length())
                put("libraryRefreshed", refreshed != null)
                if (song != null) {
                    put("songId", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album)
                } else {
                    put(
                        "note",
                        "File saved but not found in the library; call refresh_library " +
                            "or verify the file is a playable MP3"
                    )
                }
            }.toString()
        )
    }

    /**
     * Maps the supplied folder to a directory: blank → the public Music
     * root; relative → under the Music root (created by the caller); absolute
     * → only an existing music folder known to the library.
     */
    private suspend fun resolveTargetDir(raw: String?): File? {
        val musicRoot =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (raw.isNullOrBlank()) return musicRoot
        if (File(raw).isAbsolute) {
            return songRepository.observeFolders().first().firstOrNull { folder ->
                runCatching { File(folder).canonicalPath == File(raw).canonicalPath }
                    .getOrDefault(false)
            }?.let { File(it) }
        }
        val segments = normalizeRelativeFolder(raw) ?: return null
        return segments.fold(musicRoot) { parent, name -> File(parent, name) }
    }

    /**
     * Streams the response into `<target>.part`, validates the MP3 signature
     * and size cap, then renames onto [target]. The temp file is removed on
     * every failure path. Internal failures are returned as error outcomes.
     */
    private fun downloadToTemp(
        url: String,
        target: File,
        headers: Map<String, String>
    ): ToolOutcome {
        val temp = File(target.parentFile, target.name + ".part")
        temp.delete()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", MUSIC_BRAINZ_USER_AGENT)
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val code = connection.responseCode
            if (code !in 200..299) {
                return ToolOutcome("HTTP $code", isError = true)
            }
            val contentType = connection.contentType.orEmpty()
            if (connection.contentLengthLong > MAX_AUDIO_BYTES) {
                return ToolOutcome("File exceeds the 200 MB limit", isError = true)
            }
            if (contentType.contains("text/html") || contentType.startsWith("image/") ||
                contentType.startsWith("video/") || contentType.contains("json")
            ) {
                return ToolOutcome(
                    "URL did not return audio (content-type: $contentType)", isError = true
                )
            }
            val head = ByteArray(3)
            var headLength = 0
            var written = 0L
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (headLength < head.size) {
                            val take = minOf(head.size - headLength, read)
                            System.arraycopy(buffer, 0, head, headLength, take)
                            headLength += take
                        }
                        written += read
                        if (written > MAX_AUDIO_BYTES) {
                            return ToolOutcome(
                                "Download exceeded the 200 MB limit", isError = true
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (written == 0L) {
                return ToolOutcome("Empty response body", isError = true)
            }
            if (!looksLikeMp3(head, headLength)) {
                return ToolOutcome(
                    "URL did not return an MP3 file (content-type: $contentType)", isError = true
                )
            }
            if (!temp.renameTo(target)) {
                return ToolOutcome("Could not finalize ${target.name}", isError = true)
            }
            return ToolOutcome("ok")
        } catch (e: Exception) {
            return ToolOutcome(
                "Download failed: ${e.message ?: "network error"}", isError = true
            )
        } finally {
            connection.disconnect()
            temp.delete()
        }
    }
}
