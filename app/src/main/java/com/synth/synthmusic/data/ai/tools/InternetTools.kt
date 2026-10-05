package com.synth.synthmusic.data.ai.tools

import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.WebSearchProvider
import com.synth.synthmusic.domain.repository.AiSettingsRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.ai.AiTool
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

/** Shared HTTP policy values for internet tools. */
internal const val CONNECT_TIMEOUT_MS = 20_000
internal const val READ_TIMEOUT_MS = 60_000
internal const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024

/** User-Agent per MusicBrainz etiquette. */
internal const val MUSIC_BRAINZ_USER_AGENT = "SynthMusic/0.1 (Android)"

/**
 * SSRF guard: resolves the host and rejects loopback/link-local/private
 * addresses. Local AI *provider* endpoints are unaffected — this guard only
 * applies to model-supplied URLs in internet tools.
 */
internal fun isBlockedHost(url: String): Boolean = runCatching {
    val host = URL(url).host
    val addresses = InetAddress.getAllByName(host)
    addresses.any { addr ->
        addr.isLoopbackAddress || addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress || addr.isAnyLocalAddress
    }
}.getOrDefault(true)

/**
 * Performs a GET request and returns (status, body, contentType).
 */
internal fun httpGet(url: String, headers: Map<String, String> = emptyMap()): Triple<Int, String, String> {
    val connection = URL(url).openConnection() as java.net.HttpURLConnection
    connection.connectTimeout = CONNECT_TIMEOUT_MS
    connection.readTimeout = READ_TIMEOUT_MS
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("User-Agent", MUSIC_BRAINZ_USER_AGENT)
    headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
    return try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.use { it.readBytes().decodeToString().take(2_000_000) }.orEmpty()
        Triple(code, body, connection.contentType.orEmpty())
    } finally {
        connection.disconnect()
    }
}

internal fun urlEncode(value: String): String =
    URLEncoder.encode(value, "UTF-8")

/**
 * `search_music_metadata` — iTunes Search API (no key required).
 */
class SearchMusicMetadataTool : AiTool {

    override val name = "search_music_metadata"
    override val description = "Search the iTunes catalog for songs, albums or artists. " +
        "Returns title/artist/album/year and an artworkUrl usable with embed_artwork."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("query", ToolSchemas.queryProperty("Search terms (required)"))
            putJsonObject("entity") {
                put("type", "string")
                putJsonArray("enum") {
                    listOf("song", "album", "artist").forEach { add(JsonPrimitive(it)) }
                }
                put("description", "What to search for")
            }
            put("limit", ToolSchemas.intProperty("Max results (1-25)", 1, 25))
        }
        putJsonArray("required") { add(JsonPrimitive("query")) }
    }
    override val requiredGrants = setOf(AiCapability.INTERNET)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome =
        withContext(Dispatchers.IO) {
            val query = args["query"]?.jsonPrimitive?.content
                ?: return@withContext ToolOutcome("Missing query", isError = true)
            val entity = args["entity"]?.jsonPrimitive?.content ?: "song"
            val limit = (args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 10).coerceIn(1, 25)
            val url = "https://itunes.apple.com/search?term=${urlEncode(query)}" +
                "&entity=${urlEncode(entity)}&limit=$limit"
            if (isBlockedHost(url)) {
                return@withContext ToolOutcome("Blocked host", isError = true)
            }
            val (code, body, _) = httpGet(url)
            if (code != 200) {
                return@withContext ToolOutcome("iTunes returned HTTP $code", isError = true)
            }
            val results = runCatching {
                val json = Json { ignoreUnknownKeys = true }
                json.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty()
                    .map { it.jsonObject }
            }.getOrDefault(emptyList())
            ToolOutcome(
                buildJsonObject {
                    put("count", results.size)
                    put("results", buildJsonArray {
                        results.forEach { r ->
                            add(buildJsonObject {
                                r["trackName"]?.let { put("title", it.jsonPrimitive.content) }
                                r["collectionName"]?.let { put("album", it.jsonPrimitive.content) }
                                r["artistName"]?.let { put("artist", it.jsonPrimitive.content) }
                                r["releaseDate"]?.let { put("year", it.jsonPrimitive.content.take(4)) }
                                r["artworkUrl100"]?.let {
                                    put(
                                        "artworkUrl",
                                        it.jsonPrimitive.content.replace("100x100", "1200x1200")
                                    )
                                }
                            })
                        }
                    })
                }.toString()
            )
        }
}

/**
 * `search_release` — MusicBrainz release search with Cover Art Archive links.
 */
class SearchReleaseTool : AiTool {

    override val name = "search_release"
    override val description = "Deeper release search via MusicBrainz. Returns release titles, " +
        "artists, years and Cover Art Archive URLs for artwork embedding."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("query", ToolSchemas.queryProperty("Release search terms (required)"))
            put("limit", ToolSchemas.intProperty("Max results (1-10)", 1, 10))
        }
        putJsonArray("required") { add(JsonPrimitive("query")) }
    }
    override val requiredGrants = setOf(AiCapability.INTERNET)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome =
        withContext(Dispatchers.IO) {
            val query = args["query"]?.jsonPrimitive?.content
                ?: return@withContext ToolOutcome("Missing query", isError = true)
            val limit = (args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5).coerceIn(1, 10)
            val url = "https://musicbrainz.org/ws/2/release/?query=${urlEncode(query)}" +
                "&fmt=json&limit=$limit"
            if (isBlockedHost(url)) {
                return@withContext ToolOutcome("Blocked host", isError = true)
            }
            val (code, body, _) = httpGet(url)
            if (code != 200) {
                return@withContext ToolOutcome("MusicBrainz returned HTTP $code", isError = true)
            }
            val releases = runCatching {
                Json { ignoreUnknownKeys = true }.parseToJsonElement(body)
                    .jsonObject["releases"]?.jsonArray.orEmpty().map { it.jsonObject }
            }.getOrDefault(emptyList())
            ToolOutcome(
                buildJsonObject {
                    put("count", releases.size)
                    put("releases", buildJsonArray {
                        releases.forEach { r ->
                            add(buildJsonObject {
                                r["title"]?.let { put("title", it.jsonPrimitive.content) }
                                val artist = (r["artist-credit"] as? kotlinx.serialization.json.JsonArray)
                                    ?.firstOrNull()?.jsonObject?.get("name")
                                    ?.jsonPrimitive?.content
                                artist?.let { put("artist", it) }
                                r["date"]?.let { put("date", it.jsonPrimitive.content) }
                                r["id"]?.let { mbid ->
                                    put(
                                        "artworkUrl",
                                        "https://coverartarchive.org/release/${mbid.jsonPrimitive.content}/front"
                                    )
                                }
                            })
                        }
                    })
                }.toString()
            )
        }
}

/**
 * `get_lyrics_online` — LRCLIB lyrics search (plain + synced).
 */
class GetLyricsOnlineTool : AiTool {

    override val name = "get_lyrics_online"
    override val description = "Fetch lyrics for a track from LRCLIB. Returns plain lyrics and " +
        "optionally synced lyrics with [mm:ss.xx] timestamps; feed the result to set_lyrics."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("track", ToolSchemas.queryProperty("Track title"))
            put("artist", ToolSchemas.queryProperty("Artist name"))
        }
        putJsonArray("required") {
            add(JsonPrimitive("track")); add(JsonPrimitive("artist"))
        }
    }
    override val requiredGrants = setOf(AiCapability.INTERNET)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome =
        withContext(Dispatchers.IO) {
            val track = args["track"]?.jsonPrimitive?.content
                ?: return@withContext ToolOutcome("Missing track", isError = true)
            val artist = args["artist"]?.jsonPrimitive?.content
                ?: return@withContext ToolOutcome("Missing artist", isError = true)
            val url = "https://lrclib.net/api/search?track=${urlEncode(track)}" +
                "&artist=${urlEncode(artist)}"
            if (isBlockedHost(url)) {
                return@withContext ToolOutcome("Blocked host", isError = true)
            }
            val (code, body, _) = httpGet(url)
            if (code != 200) {
                return@withContext ToolOutcome("LRCLIB returned HTTP $code", isError = true)
            }
            val records = runCatching {
                Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonArray
                    .map { it.jsonObject }
            }.getOrDefault(emptyList())
            val best = records.firstOrNull {
                !it["plainLyrics"]?.jsonPrimitive?.content.isNullOrBlank()
            }
            if (best == null) {
                return@withContext ToolOutcome("No lyrics found for \"$track\" by \"$artist\"")
            }
            ToolOutcome(
                buildJsonObject {
                    best["trackName"]?.let { put("track", it.jsonPrimitive.content) }
                    best["artistName"]?.let { put("artist", it.jsonPrimitive.content) }
                    best["plainLyrics"]?.let { put("plain", it.jsonPrimitive.content) }
                    best["syncedLyrics"]?.let {
                        if (!it.jsonPrimitive.content.isBlank()) {
                            put("synced", it.jsonPrimitive.content)
                        }
                    }
                }.toString()
            )
        }
}

/**
 * `web_search` — Brave/Tavily with a user-configured key; not advertised
 * when no key is set (dispatcher filters by required INTERNET only, so the
 * tool itself reports the missing configuration).
 */
class WebSearchTool(
    private val settingsRepository: AiSettingsRepository
) : AiTool {

    override val name = "web_search"
    override val description = "General web search (top 5 results: title, url, snippet). " +
        "Requires the user to configure a Brave or Tavily API key in AI settings."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("query", ToolSchemas.queryProperty("Search query"))
        }
        putJsonArray("required") { add(JsonPrimitive("query")) }
    }
    override val requiredGrants = setOf(AiCapability.INTERNET)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome =
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.current()
            val key = settingsRepository.getWebSearchKey()
            if (settings.webSearchProvider == WebSearchProvider.NONE || key.isNullOrBlank()) {
                return@withContext ToolOutcome(
                    "No web search provider configured; ask the user to add one in AI settings",
                    isError = true
                )
            }
            val query = args["query"]?.jsonPrimitive?.content
                ?: return@withContext ToolOutcome("Missing query", isError = true)
            val results: List<JsonObject> = when (settings.webSearchProvider) {
                WebSearchProvider.BRAVE -> {
                    val url = "https://api.search.brave.com/res/v1/web/search?q=${urlEncode(query)}"
                    val (code, body, _) = httpGet(
                        url,
                        headers = mapOf(
                            "X-Subscription-Token" to key,
                            "Accept" to "application/json"
                        )
                    )
                    if (code != 200) {
                        return@withContext ToolOutcome("Brave returned HTTP $code", isError = true)
                    }
                    parseBraveResults(body)
                }
                WebSearchProvider.TAVILY -> {
                    val payload = buildJsonObject {
                        put("query", query)
                        put("max_results", 5)
                    }.toString()
                    val connection =
                        URL("https://api.tavily.com/search").openConnection()
                            as java.net.HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer $key")
                    connection.outputStream.use { it.write(payload.toByteArray()) }
                    val code = connection.responseCode
                    val body =
                        (if (code in 200..299) connection.inputStream else connection.errorStream)
                            ?.use { it.readBytes().decodeToString() }.orEmpty()
                    connection.disconnect()
                    if (code != 200) {
                        return@withContext ToolOutcome("Tavily returned HTTP $code", isError = true)
                    }
                    parseTavilyResults(body)
                }
                WebSearchProvider.NONE -> emptyList()
            }
            ToolOutcome(
                buildJsonObject {
                    put("count", results.size)
                    put("results", buildJsonArray { results.forEach { add(it) } })
                }.toString()
            )
        }

    private fun parseBraveResults(body: String): List<JsonObject> = runCatching {
        val json = Json { ignoreUnknownKeys = true }
        json.parseToJsonElement(body).jsonObject["web"]?.jsonObject
            ?.get("results")?.jsonArray.orEmpty()
            .take(5)
            .map { r ->
                val obj = r.jsonObject
                buildJsonObject {
                    obj["title"]?.let { put("title", it.jsonPrimitive.content) }
                    obj["url"]?.let { put("url", it.jsonPrimitive.content) }
                    obj["description"]?.let { put("snippet", it.jsonPrimitive.content) }
                }
            }
    }.getOrDefault(emptyList())

    private fun parseTavilyResults(body: String): List<JsonObject> = runCatching {
        val json = Json { ignoreUnknownKeys = true }
        json.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty()
            .take(5)
            .map { r ->
                val obj = r.jsonObject
                buildJsonObject {
                    obj["title"]?.let { put("title", it.jsonPrimitive.content) }
                    obj["url"]?.let { put("url", it.jsonPrimitive.content) }
                    obj["content"]?.let { put("snippet", it.jsonPrimitive.content) }
                }
            }
    }.getOrDefault(emptyList())
}

/**
 * `fetch_url` — GET an http(s) page and return extracted plain text
 * (scripts/styles/tags stripped, `<title>` kept), capped at 24 KB.
 */
class FetchUrlTool : AiTool {

    override val name = "fetch_url"
    override val description = "Fetch a web page and return its plain text (HTML stripped, " +
        "max 24 KB). Only http/https; local/private addresses are blocked."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("url", ToolSchemas.queryProperty("Absolute http(s) URL"))
        }
        putJsonArray("required") { add(JsonPrimitive("url")) }
    }
    override val requiredGrants = setOf(AiCapability.INTERNET)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome =
        withContext(Dispatchers.IO) {
            val url = args["url"]?.jsonPrimitive?.content
                ?: return@withContext ToolOutcome("Missing url", isError = true)
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@withContext ToolOutcome("Only http/https URLs are allowed", isError = true)
            }
            if (isBlockedHost(url)) {
                return@withContext ToolOutcome("Blocked host", isError = true)
            }
            val (code, body, contentType) = httpGet(url)
            if (code != 200) {
                return@withContext ToolOutcome("HTTP $code", isError = true)
            }
            if (!contentType.contains("text/html") && !contentType.contains("text/plain")) {
                return@withContext ToolOutcome(
                    "Unsupported content type: $contentType", isError = true
                )
            }
            ToolOutcome(extractText(body).take(MAX_TEXT_CHARS))
        }

    internal fun extractText(html: String): String {
        val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.trim()
        val withoutScripts = html
            .replace(Regex("<(script|style)[^>]*>.*?</\\1>", RegexOption.DOT_MATCHES_ALL), " ")
        val text = withoutScripts
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
        return (title?.let { "$title\n\n" } ?: "") + text
    }

    private companion object {
        const val MAX_TEXT_CHARS = 24_000
    }
}

/**
 * `get_song_artwork` — vision-only tool: loads a song's artwork bytes into
 * the conversation as an image part via the context's image sink.
 */
class GetSongArtworkTool(
    private val appContext: android.content.Context,
    private val songRepository: SongRepository
) : AiTool {

    override val name = "get_song_artwork"
    override val description = "Show the current artwork of a song to you (vision models only). " +
        "Use it to visually inspect covers before embedding or removing them."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("songId", buildJsonObject { put("type", "string") })
        }
        putJsonArray("required") { add(JsonPrimitive("songId")) }
    }
    override val requiredGrants = setOf(AiCapability.READ_LIBRARY)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val songId = args["songId"]?.jsonPrimitive?.content
            ?: return ToolOutcome("Missing songId", isError = true)
        val song = songRepository.getSongById(songId)
            ?: return ToolOutcome("Unknown song id: $songId", isError = true)
        val artworkUri = song.artworkUri
        if (artworkUri.isNullOrBlank()) {
            return ToolOutcome("Song \"${song.title}\" has no artwork")
        }
        val bytes = runCatching {
            val uri = android.net.Uri.parse(artworkUri)
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: java.io.File(artworkUri).takeIf { it.exists() }?.readBytes()
        }.getOrNull()
        if (bytes == null) {
            return ToolOutcome("Could not load artwork bytes", isError = true)
        }
        context.imageSink.add(
            AiMessagePart.Image(mimeType = "image/jpeg", base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        )
        return ToolOutcome("Artwork of \"${song.title}\" attached to the conversation")
    }
}


/**
 * Stores downloaded images in the app cache and resolves ids back to files;
 * the indirection keeps large binaries out of the model context.
 */
class DownloadStore(context: android.content.Context) {

    private val dir = java.io.File(context.cacheDir, "ai_downloads").apply { mkdirs() }

    /**
     * Saves [bytes] and returns a short attachment id.
     */
    fun save(bytes: ByteArray, extension: String = "img"): String {
        val id = java.util.UUID.randomUUID().toString().take(8)
        java.io.File(dir, "$id.$extension").writeBytes(bytes)
        return id
    }

    /**
     * Resolves an attachment id to the cached file, or null.
     */
    fun resolve(id: String): java.io.File? =
        dir.listFiles { f -> f.nameWithoutExtension == id }?.firstOrNull()
}

/**
 * `download_image` — validates and caches an image URL, returning an
 * attachment id that `embed_artwork` accepts via `attachmentId`.
 */
class DownloadImageTool(
    private val downloadStore: DownloadStore
) : AiTool {

    override val name = "download_image"
    override val description = "Download an image (http/https, image/* only, max 8 MB) into a " +
        "local attachment. Returns an attachmentId that embed_artwork accepts as attachmentId."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("url", ToolSchemas.queryProperty("Absolute image URL"))
        }
        putJsonArray("required") { add(JsonPrimitive("url")) }
    }
    override val requiredGrants = setOf(AiCapability.INTERNET)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome =
        withContext(Dispatchers.IO) {
            val url = args["url"]?.jsonPrimitive?.content
                ?: return@withContext ToolOutcome("Missing url", isError = true)
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@withContext ToolOutcome("Only http/https URLs are allowed", isError = true)
            }
            if (isBlockedHost(url)) {
                return@withContext ToolOutcome("Blocked host", isError = true)
            }
            val connection = URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            val contentType = connection.contentType.orEmpty()
            val bytes = runCatching { connection.inputStream.use { it.readBytes() } }
                .getOrNull()
            connection.disconnect()
            if (!contentType.startsWith("image/")) {
                return@withContext ToolOutcome(
                    "URL did not return an image (content-type: $contentType)",
                    isError = true
                )
            }
            if (bytes == null || bytes.size > MAX_DOWNLOAD_BYTES) {
                return@withContext ToolOutcome("Image missing or exceeds the 8 MB limit", isError = true)
            }
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) {
                return@withContext ToolOutcome("Data is not a decodable image", isError = true)
            }
            val id = downloadStore.save(bytes)
            ToolOutcome("attachment:$id — pass this id to embed_artwork as attachmentId")
        }
}
