package com.synth.synthmusic.data.ai.customtools

import android.content.Context
import com.synth.synthmusic.data.ai.tools.AudioFileDownloader
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.usecase.ai.AiTool
import com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel
import com.synth.synthmusic.domain.usecase.ai.CustomToolProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * UI-facing snapshot of an installed custom tool: validated manifest data
 * plus its enable state, or a load error for tools that fail validation
 * (kept visible so they can be inspected and deleted).
 */
data class CustomToolRecord(
    val name: String,
    val displayName: String,
    val description: String,
    val author: String? = null,
    val enabled: Boolean = true,
    val isScript: Boolean = false,
    val grants: Set<AiCapability> = emptySet(),
    val confirmationLevel: ConfirmationLevel = ConfirmationLevel.CONFIRM,
    val paramsSchemaJson: String = "{}",
    val secretRefs: Set<String> = emptySet(),
    val loadError: String? = null
)

/**
 * Registry of user-imported custom tools. Loads packages from
 * [CustomToolStore], validates them against the manifest rules, adapts them
 * to [AiTool]s ([HttpCustomTool] / [ScriptedCustomTool]) and exposes them to
 * the dispatcher via [CustomToolProvider]. The loaded snapshot is cached and
 * invalidated on import/delete/enable changes.
 */
class CustomToolRepository(
    context: Context,
    private val json: Json,
    private val httpClient: OkHttpClient,
    private val secretStore: CustomToolSecretStore,
    private val downloader: AudioFileDownloader,
    private val songRepository: com.synth.synthmusic.domain.repository.SongRepository,
    private val builtInToolNames: () -> Set<String>
) : CustomToolProvider {

    private val store = CustomToolStore(context, json)
    private val reader = CustomToolPackageReader(json)

    private val mutex = Mutex()

    @Volatile
    private var cache: List<CustomToolRecord>? = null

    /** All installed tools (including disabled and broken ones) for the UI. */
    suspend fun records(): List<CustomToolRecord> = mutex.withLock { loadLocked() }

    /** Imports a `.json`/`.zip` package, replacing a tool with the same name. */
    suspend fun import(bytes: ByteArray, sourceName: String): Result<CustomToolRecord> =
        withContext(Dispatchers.IO) {
            runCatching { reader.read(bytes, sourceName) }.mapCatching { pkg ->
                val validated = validateManifest(pkg.manifest, builtInToolNames())
                store.install(validated.manifest, pkg.scriptSource, validated.scriptFile)
                mutex.withLock { cache = null }
                records().first { it.name == validated.manifest.name }
            }
        }

    /** Removes a tool and its stored secrets. */
    suspend fun delete(name: String) {
        store.delete(name)
        secretStore.removeAll(name)
        mutex.withLock { cache = null }
    }

    suspend fun setEnabled(name: String, enabled: Boolean) {
        store.setEnabled(name, enabled)
        mutex.withLock { cache = null }
    }

    /** Stores (or clears when blank) one secret value. */
    suspend fun setSecret(toolName: String, secretName: String, value: String) {
        secretStore.set(toolName, secretName, value)
    }

    /** Names of secrets that already have a stored value. */
    suspend fun storedSecretNames(toolName: String): Set<String> =
        secretStore.storedNames(toolName)

    /**
     * Builds the tool for an explicit test run from the settings screen.
     * Works even while the tool is disabled (the user invokes it directly);
     * returns null for unknown or broken tools.
     */
    suspend fun buildTool(name: String): AiTool? = mutex.withLock {
        loadLocked().firstOrNull { it.name == name && it.loadError == null }?.let { buildAdapterLocked(it) }
    }

    // --- CustomToolProvider ---

    override suspend fun activeTools(): List<AiTool> = mutex.withLock {
        loadLocked().filter { it.enabled && it.loadError == null }.mapNotNull { record ->
            buildAdapterLocked(record)
        }
    }

    override suspend fun toolByName(name: String): AiTool? = mutex.withLock {
        loadLocked().firstOrNull { it.name == name && it.enabled && it.loadError == null }
            ?.let { buildAdapterLocked(it) }
    }

    // --- internals ---

    private suspend fun loadLocked(): List<CustomToolRecord> {
        cache?.let { return it }
        val disabled = store.disabledNames()
        val loaded = store.list().map { stored ->
            toRecord(stored, stored.name !in disabled)
        }.sortedBy { it.name }
        cache = loaded
        return loaded
    }

    private fun toRecord(stored: StoredCustomTool, enabled: Boolean): CustomToolRecord {
        val manifest = runCatching { parseManifestJson(json, stored.manifestJson) }.getOrElse {
            return CustomToolRecord(
                name = stored.name,
                displayName = stored.name,
                description = "",
                enabled = enabled,
                isScript = stored.scriptSource != null,
                loadError = it.message ?: "Unreadable manifest"
            )
        }
        val validated = runCatching { validateManifest(manifest, builtInToolNames()) }.getOrElse {
            return CustomToolRecord(
                name = manifest.name.ifBlank { stored.name },
                displayName = manifest.displayName,
                description = manifest.description,
                author = manifest.author,
                enabled = enabled,
                isScript = manifest.script != null,
                loadError = it.message ?: "Invalid manifest"
            )
        }
        if (validated.scriptFile != null && stored.scriptSource == null) {
            return CustomToolRecord(
                name = validated.manifest.name,
                displayName = validated.manifest.displayName,
                description = validated.manifest.description,
                author = validated.manifest.author,
                enabled = enabled,
                isScript = true,
                loadError = "Script file '${validated.scriptFile}' is missing"
            )
        }
        val secretRefs = validated.secretRefs +
            secretReferences(stored.scriptSource)
        return CustomToolRecord(
            name = validated.manifest.name,
            displayName = validated.manifest.displayName,
            description = validated.manifest.description,
            author = validated.manifest.author,
            enabled = enabled,
            isScript = validated.scriptFile != null,
            grants = validated.grants,
            confirmationLevel = validated.confirmationLevel,
            paramsSchemaJson = validated.manifest.paramsSchema?.toString() ?: "{}",
            secretRefs = secretRefs,
            loadError = null
        )
    }

    /** Preconditions: called under [mutex] on a record with no load error. */
    private suspend fun buildAdapterLocked(record: CustomToolRecord): AiTool? {
        val stored = store.read(record.name) ?: return null
        val manifest = runCatching { parseManifestJson(json, stored.manifestJson) }.getOrNull()
            ?: return null
        val validated = runCatching { validateManifest(manifest, builtInToolNames()) }.getOrNull()
            ?: return null
        return if (manifest.http != null) {
            HttpCustomTool(validated, httpClient, secretStore)
        } else {
            val script = stored.scriptSource
                ?: return null
            ScriptedCustomTool(validated, script, httpClient, secretStore, downloader, songRepository)
        }
    }
}
