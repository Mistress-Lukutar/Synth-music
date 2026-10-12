package com.synth.synthmusic.data.ai.customtools

import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Current manifest format understood by this build. */
const val CUSTOM_TOOL_FORMAT_VERSION = 1

/** Manifest file name expected inside an imported package. */
const val CUSTOM_TOOL_MANIFEST_FILE = "manifest.json"

/** Default file name of the script referenced by [CustomToolManifest.script]. */
const val CUSTOM_TOOL_DEFAULT_SCRIPT_FILE = "tool.js"

/**
 * Declarative description of a user-imported custom AI tool. A package is a
 * JSON file (HTTP execution only) or a zip containing this manifest plus the
 * optional script file. Everything the model sees (name, description,
 * paramsSchema) and everything the user approves (grants, confirmation) is
 * declared here — no code ships inside the app.
 */
@Serializable
data class CustomToolManifest(

    /** Must be [CUSTOM_TOOL_FORMAT_VERSION]. */
    val formatVersion: Int = CUSTOM_TOOL_FORMAT_VERSION,

    /** Unique snake_case tool name, e.g. `hitmoz_download`. */
    val name: String = "",

    /** Human-readable title shown in settings; defaults to [name]. */
    val title: String? = null,

    /** Model-facing usage guidance (English recommended). */
    val description: String = "",

    /** Optional author string shown in settings. */
    val author: String? = null,

    /** Capability names the tool depends on: [AiCapability] entries. */
    val grants: List<String> = emptyList(),

    /** Confirmation policy: none / confirm / always_confirm. */
    val confirmation: String? = null,

    /** JSON Schema object describing the parameters. */
    val paramsSchema: JsonObject? = null,

    /** Declared secret names; also auto-detected from references. */
    val secrets: List<String> = emptyList(),

    /** Allow plain http:// targets (default: https only). */
    val allowHttp: Boolean = false,

    /** Declarative single-request execution; mutually exclusive with [script]. */
    val http: HttpExecutionSpec? = null,

    /** Script file name inside the package; mutually exclusive with [http]. */
    val script: String? = null
) {
    /** Model-facing name; the title is only for settings UI. */
    val displayName: String get() = title?.takeIf { it.isNotBlank() } ?: name
}

/**
 * Declarative HTTP execution: substitute `{param}` placeholders from the
 * tool arguments, append [query], send, optionally extract from the response.
 */
@Serializable
data class HttpExecutionSpec(

    /** URL template with `{param}` placeholders. */
    val url: String = "",

    /** HTTP method; defaults to GET. */
    val method: String? = null,

    /** Static headers; values support `{param}` and `{{secret:name}}`. */
    val headers: Map<String, String> = emptyMap(),

    /** Query parameters appended to the URL; values support placeholders. */
    val query: Map<String, String> = emptyMap(),

    /** Request body template (POST/PUT/PATCH); supports placeholders. */
    val bodyTemplate: String? = null,

    /** Content type of [bodyTemplate]; defaults to application/json. */
    val contentType: String? = null,

    /**
     * Response extraction: `$.path.to.field[0]` (JSON path), `regex:…`
     * (first match, capture groups joined), or absent for the raw body.
     */
    val responseExtract: String? = null
)

/** Raised at import time when a manifest violates the format rules. */
class CustomToolValidationException(val errors: List<String>) :
    Exception(errors.joinToString("; "))

/**
 * Validated, normalized view of a [CustomToolManifest]: grants resolved to
 * [AiCapability], confirmation policy parsed, placeholder names checked.
 */
data class ValidatedCustomTool(
    val manifest: CustomToolManifest,
    val grants: Set<AiCapability>,
    val confirmationLevel: ConfirmationLevel,
    val scriptFile: String?,
    /** Parameter names referenced via `{param}` in the HTTP spec. */
    val referencedParams: Set<String>,
    /** Secret names collected from [CustomToolManifest.secrets] and references. */
    val secretRefs: Set<String>
)

private val NAME_REGEX = Regex("^[a-z][a-z0-9_]{1,63}$")
private val SCRIPT_FILE_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
private val PLACEHOLDER_REGEX = Regex("\\{([a-zA-Z0-9_]+)\\}")
private val SECRET_REF_REGEX = Regex("\\{\\{secret:([a-zA-Z0-9_]+)\\}\\}")

/** Collects `{param}` placeholder names from all template strings. */
internal fun templatePlaceholders(vararg templates: String?): Set<String> =
    templates.filterNotNull().flatMap { PLACEHOLDER_REGEX.findAll(it) }
        .map { it.groupValues[1] }.toSet()

/** Collects `{{secret:name}}` references from any tool content. */
internal fun secretReferences(vararg contents: String?): Set<String> =
    contents.filterNotNull().flatMap { SECRET_REF_REGEX.findAll(it) }
        .map { it.groupValues[1] }.toSet()

/** Validates a secret/parameter name against the identifier charset. */
internal fun isValidIdentifier(raw: String): Boolean = NAME_REGEX.matches(raw)

/**
 * Validates [manifest] against the format rules. [builtInNames] are names
 * that must not be shadowed. Throws [CustomToolValidationException] listing
 * every problem; returns the normalized view on success.
 */
fun validateManifest(
    manifest: CustomToolManifest,
    builtInNames: Set<String>
): ValidatedCustomTool {
    val errors = mutableListOf<String>()

    if (manifest.formatVersion != CUSTOM_TOOL_FORMAT_VERSION) {
        errors.add("Unsupported formatVersion ${manifest.formatVersion}; expected $CUSTOM_TOOL_FORMAT_VERSION")
    }
    if (!NAME_REGEX.matches(manifest.name)) {
        errors.add("name '${manifest.name}' must match [a-z][a-z0-9_]{1,63}")
    }
    if (manifest.name in builtInNames) {
        errors.add("name '${manifest.name}' collides with a built-in tool")
    }
    if (manifest.description.isBlank()) {
        errors.add("description must not be empty")
    } else if (manifest.description.length > 4000) {
        errors.add("description is longer than 4000 characters")
    }

    val grants = mutableSetOf<AiCapability>()
    manifest.grants.forEach { raw ->
        val grant = runCatching { AiCapability.valueOf(raw.trim().uppercase()) }.getOrNull()
        if (grant == null) {
            errors.add("unknown grant '$raw' (valid: ${AiCapability.entries.joinToString()})")
        } else {
            grants.add(grant)
        }
    }
    if (grants.isEmpty()) {
        errors.add("grants must list at least one capability")
    }

    val confirmation = when (manifest.confirmation?.trim()?.lowercase()) {
        null, "", "confirm" -> ConfirmationLevel.CONFIRM
        "none" -> ConfirmationLevel.NONE
        "always_confirm" -> ConfirmationLevel.ALWAYS_CONFIRM
        else -> {
            errors.add("confirmation must be none, confirm or always_confirm")
            ConfirmationLevel.CONFIRM
        }
    }

    val schema = manifest.paramsSchema
    if (schema == null) {
        errors.add("paramsSchema is required")
    } else if (!schema.containsKey("type") && !schema.containsKey("properties")) {
        errors.add("paramsSchema must look like a JSON Schema object ('type' or 'properties')")
    }
    val schemaParams = schema?.let { paramsSchemaProperties(it) } ?: emptySet()

    manifest.secrets.forEach { secret ->
        if (!isValidIdentifier(secret)) {
            errors.add("secret name '$secret' must match [a-z][a-z0-9_]{1,63}")
        }
    }

    val executionCount = listOf(manifest.http != null, manifest.script != null).count { it }
    if (executionCount == 0) {
        errors.add("either 'http' or 'script' execution must be defined")
    } else if (executionCount > 1) {
        errors.add("'http' and 'script' are mutually exclusive")
    }

    var scriptFile: String? = null
    var referencedParams = emptySet<String>()
    var secretRefs = manifest.secrets.toSet()

    manifest.http?.let { http ->
        if (http.url.isBlank()) {
            errors.add("http.url must not be empty")
        } else {
            val scheme = runCatching {
                regexScheme(http.url)
            }.getOrNull()
            if (scheme == "https") {
                // allowed
            } else if (scheme == "http" && manifest.allowHttp) {
                // explicitly opted in
            } else if (scheme == "http") {
                errors.add("http.url must use https (set allowHttp=true to allow plain http)")
            } else {
                errors.add("http.url must be an absolute http(s) URL")
            }
        }
        val method = http.method?.trim()?.uppercase() ?: "GET"
        if (method !in setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE")) {
            errors.add("http.method '$method' is not supported")
        }
        if (http.bodyTemplate != null && method !in setOf("POST", "PUT", "PATCH")) {
            errors.add("http.bodyTemplate requires a POST/PUT/PATCH method")
        }
        val placeholders = templatePlaceholders(
            http.url,
            *http.query.values.toTypedArray(),
            *http.headers.values.toTypedArray(),
            http.bodyTemplate
        )
        placeholders.forEach { placeholder ->
            if (placeholder !in schemaParams) {
                errors.add("placeholder {$placeholder} is not a paramsSchema property")
            }
        }
        referencedParams = placeholders
        secretRefs = secretRefs + secretReferences(
            http.url, http.bodyTemplate, *http.headers.values.toTypedArray()
        )
        secretReferences(http.responseExtract).forEach {
            errors.add("secrets are not supported inside responseExtract")
        }
    }

    manifest.script?.let { file ->
        if (!SCRIPT_FILE_REGEX.matches(file) || file.endsWith(".js").not()) {
            errors.add("script must be a relative .js file name (e.g. '$CUSTOM_TOOL_DEFAULT_SCRIPT_FILE')")
        }
        scriptFile = file
    }

    if (errors.isNotEmpty()) throw CustomToolValidationException(errors)

    return ValidatedCustomTool(
        manifest = manifest,
        grants = grants,
        confirmationLevel = confirmation,
        scriptFile = scriptFile,
        referencedParams = referencedParams,
        secretRefs = secretRefs
    )
}

private fun regexScheme(url: String): String? =
    Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):").find(url)?.groupValues?.get(1)?.lowercase()

/**
 * Property names declared at the top level of a params schema, used to
 * verify that URL/body placeholders reference real parameters.
 */
internal fun paramsSchemaProperties(schema: JsonObject): Set<String> {
    val properties = schema["properties"] as? JsonObject ?: return emptySet()
    return properties.keys
}
