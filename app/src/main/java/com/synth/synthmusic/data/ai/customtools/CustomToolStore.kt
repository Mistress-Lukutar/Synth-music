package com.synth.synthmusic.data.ai.customtools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A tool directory on disk: manifest text plus optional script source. */
data class StoredCustomTool(
    val name: String,
    val manifestJson: String,
    val scriptSource: String?,
    val scriptFile: String?
)

/** Persisted enable/disable state (tools default to enabled). */
@Serializable
private data class CustomToolState(val disabled: List<String> = emptyList())

/**
 * File-system storage for imported custom tools under
 * `filesDir/custom_tools/<name>/` (manifest.json + script) plus a small
 * state file with enable/disable flags. No database involvement: a tool
 * directory is directly shareable by copying it.
 */
class CustomToolStore(private val context: Context, private val json: Json) {

    private val root: File get() = File(context.filesDir, ROOT_DIR)
    private val stateFile: File get() = File(root, STATE_FILE)

    /**
     * Lists every installed tool directory. Unparsable manifests are
     * reported through [CustomToolPackageException]-free raw text so the
     * repository can surface a load error instead of dropping the tool.
     */
    suspend fun list(): List<StoredCustomTool> = withContext(Dispatchers.IO) {
        val dirs = root.listFiles { file -> file.isDirectory } ?: return@withContext emptyList()
        dirs.mapNotNull { dir ->
            val manifestFile = File(dir, CUSTOM_TOOL_MANIFEST_FILE)
            if (!manifestFile.isFile) return@mapNotNull null
            val scriptFile = dir.listFiles { f -> f.isFile && f.name.endsWith(".js") }
                ?.maxByOrNull { it.name }
            StoredCustomTool(
                name = dir.name,
                manifestJson = runCatching { manifestFile.readText() }.getOrDefault(""),
                scriptSource = scriptFile?.let { runCatching { it.readText() }.getOrNull() },
                scriptFile = scriptFile?.name
            )
        }.sortedBy { it.name }
    }

    /** Reads a single installed tool directory, or null when absent. */
    suspend fun read(name: String): StoredCustomTool? = withContext(Dispatchers.IO) {
        val dir = File(root, name)
        val manifestFile = File(dir, CUSTOM_TOOL_MANIFEST_FILE)
        if (!manifestFile.isFile) return@withContext null
        val scriptFile = dir.listFiles { f -> f.isFile && f.name.endsWith(".js") }
            ?.maxByOrNull { it.name }
        StoredCustomTool(
            name = name,
            manifestJson = runCatching { manifestFile.readText() }.getOrDefault(""),
            scriptSource = scriptFile?.let { runCatching { it.readText() }.getOrNull() },
            scriptFile = scriptFile?.name
        )
    }

    /**
     * Installs (or replaces) a package under its validated manifest name.
     * The directory is rebuilt atomically enough for tool-scale payloads:
     * files are written into place after the old directory was removed.
     */
    suspend fun install(
        manifest: CustomToolManifest,
        scriptSource: String?,
        scriptFile: String?
    ): Unit = withContext(Dispatchers.IO) {
        val dir = File(root, manifest.name)
        dir.deleteRecursively()
        dir.mkdirs()
        File(dir, CUSTOM_TOOL_MANIFEST_FILE)
            .writeText(json.encodeToString(CustomToolManifest.serializer(), manifest))
        if (scriptSource != null && scriptFile != null) {
            File(dir, scriptFile).writeText(scriptSource)
        }
    }

    suspend fun delete(name: String): Unit = withContext(Dispatchers.IO) {
        File(root, name).deleteRecursively()
    }

    /** Persists the enabled flag; default is enabled. */
    suspend fun setEnabled(name: String, enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        val state = readState()
        val disabled = state.disabled.toMutableList()
        if (enabled) disabled.remove(name) else if (name !in disabled) disabled.add(name)
        writeState(CustomToolState(disabled = disabled))
    }

    suspend fun disabledNames(): Set<String> = withContext(Dispatchers.IO) { readState().disabled.toSet() }

    private fun readState(): CustomToolState = runCatching {
        json.decodeFromString(CustomToolState.serializer(), stateFile.readText())
    }.getOrDefault(CustomToolState())

    private fun writeState(state: CustomToolState) {
        root.mkdirs()
        stateFile.writeText(json.encodeToString(CustomToolState.serializer(), state))
    }

    companion object {
        private const val ROOT_DIR = "custom_tools"
        private const val STATE_FILE = "state.json"
    }
}
