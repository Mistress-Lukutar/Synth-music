package com.synth.synthmusic.data.ai.customtools

import com.synth.synthmusic.domain.usecase.ai.AiTool
import com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Response bodies larger than this are refused (the model needs compact results anyway). */
private const val MAX_RESPONSE_BYTES = 1L * 1024 * 1024

/** Hard wall-clock limit for one declarative HTTP execution. */
private const val EXECUTION_TIMEOUT_MS = 30_000L

/** Placeholder pattern substituted from tool arguments. */
private val PLACEHOLDER = Regex("\\{([a-zA-Z0-9_]+)\\}")

/** `{{secret:name}}` pattern resolved from the encrypted secret store. */
private val SECRET_REF = Regex("\\{\\{secret:([a-zA-Z0-9_]+)\\}\\}")

/**
 * Adapter exposing a declarative HTTP tool (manifest with an `http` block)
 * as a regular [AiTool]: substitutes `{param}` placeholders and
 * `{{secret:name}}` references, performs the single configured request and
 * optionally extracts a fragment from the response.
 */
class HttpCustomTool(
    private val validated: ValidatedCustomTool,
    private val client: OkHttpClient,
    private val secrets: CustomSecrets
) : AiTool {

    private val manifest = validated.manifest
    private val spec = requireNotNull(manifest.http)

    override val name = manifest.name
    override val description = manifest.description
    override val paramsSchema: JsonObject = manifest.paramsSchema ?: JsonObject(emptyMap())
    override val requiredGrants = validated.grants
    override val confirmationLevel = validated.confirmationLevel

    override fun summarize(args: JsonObject): String {
        val url = renderTemplate(spec.url, args, encodePath = true, secretValues = emptyMap())
            .getOrElse { spec.url }
        return "${spec.method?.uppercase() ?: "GET"} $url"
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(EXECUTION_TIMEOUT_MS.toLong()) { runRequest(args) }
            } catch (e: TimeoutCancellationException) {
                ToolOutcome("Custom tool timed out after ${EXECUTION_TIMEOUT_MS / 1000}s", isError = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CustomToolPackageException) {
                ToolOutcome(e.message ?: "Invalid custom tool configuration", isError = true)
            } catch (e: Exception) {
                ToolOutcome("Request failed: ${e.message ?: "network error"}", isError = true)
            }
        }

    private suspend fun runRequest(args: JsonObject): ToolOutcome {
        val rawUrl = substitute(spec.url, args, encodePath = true).getOrThrow()
        val urlBuilder = rawUrl.toHttpUrlOrNull()?.newBuilder()
            ?: return ToolOutcome("Invalid URL: $rawUrl", isError = true)

        spec.query.forEach { (key, template) ->
            val value = substitute(template, args, encodeQuery = true).getOrThrow()
            urlBuilder.addQueryParameter(key, value)
        }

        val method = spec.method?.uppercase() ?: "GET"
        val body = if (spec.bodyTemplate != null && method in setOf("POST", "PUT", "PATCH")) {
            val contentType = (spec.contentType ?: "application/json").trim()
            val rendered = substitute(spec.bodyTemplate!!, args, encodePath = false, jsonEscape = contentType.contains("json"))
                .getOrThrow()
            rendered.toRequestBody(contentType.toMediaTypeOrNull())
        } else {
            null
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .method(method, body)
        requestBuilder.header("User-Agent", "SynthMusic/0.1 (Android)")
        spec.headers.forEach { (key, template) ->
            requestBuilder.header(key, substitute(template, args).getOrThrow())
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val bodyText = response.body?.byteStream()?.use { input ->
                val buffer = ByteArray(8 * 1024)
                val output = StringBuilder()
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) {
                        return ToolOutcome("Response exceeds the 1 MB limit", isError = true)
                    }
                    output.append(String(buffer, 0, read, Charsets.UTF_8))
                }
                output.toString()
            } ?: ""
            if (!response.isSuccessful) {
                return ToolOutcome(
                    "HTTP ${response.code}: ${bodyText.take(300)}", isError = true
                )
            }
            return extractResponse(bodyText, spec.responseExtract)
        }
    }

    /**
     * Replaces `{param}` placeholders from [args] and `{{secret:name}}`
     * references from the secret store. Path segments are URL-encoded;
     * JSON bodies get JSON-escaped values so templates stay valid.
     */
    private suspend fun substitute(
        template: String,
        args: JsonObject,
        encodePath: Boolean = false,
        encodeQuery: Boolean = false,
        jsonEscape: Boolean = false
    ): Result<String> = runCatching {
        // Resolve every referenced secret up front (suspend) so the textual
        // substitution below stays a plain, non-suspending pass.
        val secretValues = SECRET_REF.findAll(template)
            .map { it.groupValues[1] }.toSet()
            .associateWith { secretName ->
                secrets.get(manifest.name, secretName)
                    ?: throw CustomToolPackageException(
                        "Secret '$secretName' is not configured for this tool"
                    )
            }
        renderTemplate(template, args, encodePath, encodeQuery, jsonEscape, secretValues).getOrThrow()
    }

    /** Non-suspending template rendering core shared by [substitute] and [summarize]. */
    private fun renderTemplate(
        template: String,
        args: JsonObject,
        encodePath: Boolean = false,
        encodeQuery: Boolean = false,
        jsonEscape: Boolean = false,
        secretValues: Map<String, String>
    ): Result<String> = runCatching {
        var result = SECRET_REF.replace(template) { match -> secretValues[match.groupValues[1]].orEmpty() }
        result = PLACEHOLDER.replace(result) { match ->
            val name = match.groupValues[1]
            val element = args[name]
                ?: throw CustomToolPackageException("Missing argument '$name'")
            val raw = (element as? JsonPrimitive)?.content
                ?: throw CustomToolPackageException("Argument '$name' must be a scalar")
            when {
                jsonEscape -> raw.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
                encodeQuery -> java.net.URLEncoder.encode(raw, "UTF-8")
                encodePath -> java.net.URLEncoder.encode(raw, "UTF-8").replace("+", "%20")
                else -> raw
            }
        }
        result
    }

    companion object {
        /**
         * Extracts a fragment from an HTTP response per [HttpExecutionSpec.responseExtract]:
         * `$.a.b[0].c` JSON paths, `regex:…` first matches, or the raw body.
         */
        fun extractResponse(bodyText: String, extract: String?): ToolOutcome {
            if (extract.isNullOrBlank()) return ToolOutcome(bodyText)
            return when {
                extract.startsWith("$.") -> extractJsonPath(bodyText, extract)
                extract.startsWith("regex:") -> extractRegex(bodyText, extract.removePrefix("regex:"))
                else -> ToolOutcome("Unsupported responseExtract format: $extract", isError = true)
            }
        }

        private fun extractJsonPath(bodyText: String, path: String): ToolOutcome {
            val root = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(bodyText)
            }.getOrElse {
                return ToolOutcome("Response is not JSON: ${it.message}", isError = true)
            }
            val target = navigateJsonPath(root, path)
                ?: return ToolOutcome("JSON path '$path' not found in response", isError = true)
            return ToolOutcome(
                when (target) {
                    is JsonPrimitive -> target.content
                    else -> target.toString()
                }
            )
        }

        private fun extractRegex(bodyText: String, pattern: String): ToolOutcome {
            val regex = runCatching { Regex(pattern) }.getOrElse {
                return ToolOutcome("Invalid regex: ${it.message}", isError = true)
            }
            val match = regex.find(bodyText)
                ?: return ToolOutcome("Pattern not found in response", isError = true)
            return ToolOutcome(
                if (match.groupValues.size > 1) match.groupValues[1] else match.value
            )
        }
    }
}

/**
 * Walks `$.name.list[0].field` over a parsed JSON document. Returns null
 * when any segment is missing or the shape does not match.
 */
internal fun navigateJsonPath(root: JsonElement, path: String): JsonElement? {
    if (!path.startsWith("$.")) return null
    var current: JsonElement = root
    val segmentRegex = Regex("\\.([A-Za-z0-9_-]+)|(\\[(\\d+)\\])")
    for (match in segmentRegex.findAll(path)) {
        val name = match.groupValues[1]
        val index = match.groupValues[3]
        when {
            name.isNotEmpty() -> current = (current as? JsonObject)?.get(name) ?: return null
            index.isNotEmpty() -> {
                val array = current as? JsonArray ?: return null
                val i = index.toInt()
                if (i < 0 || i >= array.size) return null
                current = array[i]
            }
        }
    }
    return current
}
