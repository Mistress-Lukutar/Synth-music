package com.synth.synthmusic.data.ai.customtools

import com.synth.synthmusic.data.ai.tools.AudioFileDownloader
import com.synth.synthmusic.data.ai.tools.SongSearchMatcher
import com.synth.synthmusic.data.ai.tools.isBlockedHost
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.ai.AiTool
import com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Response bodies larger than this are refused. */
private const val MAX_RESPONSE_BYTES = 1L * 1024 * 1024

/** Hard wall-clock limit for one script execution (includes host calls). */
private const val EXECUTION_TIMEOUT_MS = 45_000L

/**
 * Adapter exposing a scripted custom tool (manifest + tool.js) as a regular
 * [AiTool]. The script runs inside the [ToolScriptRunner] sandbox and may
 * only reach the app through the [ScriptHostContext] functions built here;
 * grant-restricted host functions verify the chat's grants at call time, so
 * a tool declared with `INTERNET` alone cannot download files even if its
 * script calls `ctx.downloadAudio`.
 */
class ScriptedCustomTool(
    private val validated: ValidatedCustomTool,
    private val scriptSource: String,
    private val client: OkHttpClient,
    private val secrets: CustomSecrets,
    private val downloader: AudioFileDownloader,
    private val songRepository: SongRepository
) : AiTool {

    private val manifest = validated.manifest

    override val name = manifest.name
    override val description = manifest.description
    override val paramsSchema: JsonObject = manifest.paramsSchema ?: JsonObject(emptyMap())
    override val requiredGrants = validated.grants
    override val confirmationLevel = validated.confirmationLevel

    private val hostContext = ToolHostContext()

    override fun summarize(args: JsonObject): String =
        ToolScriptRunner.summarize(scriptSource, args) ?: "${manifest.displayName}: ${args.toString().take(160)}"

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome =
        withContext(Dispatchers.IO) {
            hostContext.grants = context.grants
            try {
                withTimeout(EXECUTION_TIMEOUT_MS) {
                    ToolScriptRunner.run(scriptSource, args, hostContext, timeoutMs = EXECUTION_TIMEOUT_MS)
                }
            } catch (e: TimeoutCancellationException) {
                ToolOutcome("Custom tool timed out after ${EXECUTION_TIMEOUT_MS / 1000}s", isError = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToolOutcome("Custom tool failed: ${e.message ?: "unknown error"}", isError = true)
            }
        }

    /**
     * Bridges the sandboxed script to app capabilities. Every HTTP function
     * checks the INTERNET grant, [downloadAudio] additionally requires
     * MANAGE_FILES, and the library search requires READ_LIBRARY — matching
     * how built-in tools are gated.
     */
    private inner class ToolHostContext : ScriptHostContext {

        var grants: Set<AiCapability> = emptySet()

        private fun requireInternet() {
            if (AiCapability.INTERNET !in grants) {
                throw ToolHostException(
                    "User denied: this action requires the internet capability " +
                        "(enable it in AI settings)"
                )
            }
        }

        private fun requireManageFiles() {
            if (AiCapability.MANAGE_FILES !in grants) {
                throw ToolHostException(
                    "User denied: this action requires the manage_files capability " +
                        "(enable it in AI settings)"
                )
            }
        }

        private fun requireReadLibrary() {
            if (AiCapability.READ_LIBRARY !in grants) {
                throw ToolHostException(
                    "User denied: this action requires the read_library capability " +
                        "(enable it in AI settings)"
                )
            }
        }

        private fun checkUrl(url: String) {
            requireInternet()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw ToolHostException("Only http/https URLs are allowed")
            }
            if (isBlockedHost(url)) throw ToolHostException("Blocked host")
        }

        override fun httpGetText(url: String, headers: Map<String, String>): String =
            httpGet(url, headers).text

        override fun httpGet(url: String, headers: Map<String, String>): ScriptHostContext.HttpTextResponse {
            checkUrl(url)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SynthMusic/0.1 (Android)")
                .get()
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(request).execute().use { response ->
                return ScriptHostContext.HttpTextResponse(
                    status = response.code,
                    contentType = response.header("Content-Type").orEmpty(),
                    text = readBody(response)
                )
            }
        }

        override fun httpPost(
            url: String,
            body: String,
            contentType: String?,
            headers: Map<String, String>
        ): ScriptHostContext.HttpTextResponse {
            checkUrl(url)
            val mediaType = (contentType ?: "application/json").toMediaTypeOrNull()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SynthMusic/0.1 (Android)")
                .post(body.toRequestBody(mediaType))
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(request).execute().use { response ->
                return ScriptHostContext.HttpTextResponse(
                    status = response.code,
                    contentType = response.header("Content-Type").orEmpty(),
                    text = readBody(response)
                )
            }
        }

        override fun downloadAudio(
            url: String,
            folder: String?,
            fileName: String?,
            headers: Map<String, String>
        ): Map<String, Any?> {
            checkUrl(url)
            requireManageFiles()
            val outcome = kotlinx.coroutines.runBlocking {
                downloader.download(url, folder, fileName, headers)
            }
            if (outcome.isError) throw ToolHostException(outcome.content)
            return mapOf("message" to outcome.content)
        }

        override fun librarySearchSongs(query: String, limit: Int): List<Map<String, Any?>> {
            requireReadLibrary()
            val songs = kotlinx.coroutines.runBlocking { songRepository.getAllSongs() }
            val matcher = SongSearchMatcher(query)
            val strict = songs.mapNotNull { song ->
                matcher.strictScore(song.title, song.artist, song.album)?.let { song to it }
            }
            val scored = if (strict.isEmpty()) {
                songs.mapNotNull { song ->
                    matcher.partialScore(song.title, song.artist, song.album)?.let { song to it }
                }
            } else {
                strict
            }
            return scored.sortedByDescending { it.second }
                .take(limit.coerceIn(1, 100))
                .map { it.first.toCompactMap() }
        }

        override fun secret(name: String): String? =
            kotlinx.coroutines.runBlocking { secrets.get(manifest.name, name) }

        override fun log(message: String) {
            // Captured by the runner's log buffer; nothing else to do here.
        }

        private fun readBody(response: okhttp3.Response): String {
            val text = response.body?.byteStream()?.use { input ->
                val buffer = ByteArray(8 * 1024)
                val output = StringBuilder()
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) {
                        throw ToolHostException("Response exceeds the 1 MB limit")
                    }
                    output.append(String(buffer, 0, read, Charsets.UTF_8))
                }
                output.toString()
            } ?: ""
            if (!response.isSuccessful) {
                throw ToolHostException("HTTP ${response.code}: ${text.take(300)}")
            }
            return text
        }
    }
}

/** Compact song representation handed to scripts. */
private fun Song.toCompactMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "artist" to artist,
    "album" to album,
    "durationMs" to durationMs
)

/** Scalar JSON value → Kotlin primitive for [ScriptHostContext] arguments. */
internal fun JsonObject.scalar(name: String): String? =
    (this[name] as? JsonPrimitive)?.content
