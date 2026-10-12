package com.synth.synthmusic.data.ai.customtools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * A parsed custom-tool package as imported by the user: the manifest plus,
 * for script tools, the script source. Built from a raw `.json` file (HTTP
 * execution only) or a `.zip` archive (manifest.json + script file).
 */
data class CustomToolPackage(
    val manifest: CustomToolManifest,
    val scriptSource: String?,
    val sourceName: String
)

/** Raised when the package bytes cannot be read as a valid tool package. */
class CustomToolPackageException(message: String) : Exception(message)

/**
 * Reads and structurally validates imported packages. Resource limits guard
 * against accidental (or hostile) archives: entry count, per-file and total
 * uncompressed size are capped; archive entry names are sanitized so nothing
 * can escape the tool directory.
 */
class CustomToolPackageReader(private val json: Json) {

    fun read(bytes: ByteArray, sourceName: String): CustomToolPackage {
        if (bytes.size > MAX_TOTAL_BYTES) {
            throw CustomToolPackageException("Package exceeds the ${MAX_TOTAL_BYTES / 1024} KB limit")
        }
        val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        return if (isZip) readZip(bytes, sourceName) else readJsonOnly(bytes, sourceName)
    }

    private fun readJsonOnly(bytes: ByteArray, sourceName: String): CustomToolPackage {
        val manifest = parseManifest(bytes.decodeToString())
        if (manifest.script != null) {
            throw CustomToolPackageException(
                "Manifest references script '${manifest.script}' but was imported as a plain " +
                    "JSON file; package the tool as a zip containing both files"
            )
        }
        return CustomToolPackage(manifest, scriptSource = null, sourceName = sourceName)
    }

    private fun readZip(bytes: ByteArray, sourceName: String): CustomToolPackage {
        val entries = LinkedHashMap<String, ByteArray>()
        var count = 0
        var total = 0L

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                count++
                if (count > MAX_ENTRIES) {
                    throw CustomToolPackageException("Archive contains too many files (max $MAX_ENTRIES)")
                }
                val name = sanitizeEntryName(entry.name)
                if (name.isEmpty()) continue
                val data = readEntry(zip, entry.size, name)
                total += data.size
                if (total > MAX_TOTAL_BYTES) {
                    throw CustomToolPackageException("Archive exceeds the ${MAX_TOTAL_BYTES / 1024} KB limit")
                }
                entries.putIfAbsent(name, data)
            }
        }

        val manifestData = entries[CUSTOM_TOOL_MANIFEST_FILE]
            ?: throw CustomToolPackageException("Archive does not contain $CUSTOM_TOOL_MANIFEST_FILE")
        val manifest = parseManifest(manifestData.decodeToString())

        // A declared script is resolved by name; without a declaration the
        // first .js entry is accepted so a default "tool.js" package needs
        // no explicit 'script' field in the manifest.
        val declared = manifest.script
        val scriptName = declared ?: entries.keys.firstOrNull { it.endsWith(".js") }
        if (declared != null && scriptName !in entries) {
            throw CustomToolPackageException("Archive does not contain script '$declared'")
        }

        return CustomToolPackage(
            manifest = if (manifest.script == null && scriptName != null) {
                manifest.copy(script = scriptName)
            } else {
                manifest
            },
            scriptSource = scriptName?.let { entries[it] }?.decodeToString(),
            sourceName = sourceName
        )
    }

    /** Strips folder prefixes, rejecting path traversal and absolute names. */
    private fun sanitizeEntryName(raw: String): String {
        val normalized = raw.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw CustomToolPackageException("Unsafe archive entry: $raw")
        }
        return normalized.substringAfterLast('/')
    }

    private fun readEntry(zip: ZipInputStream, declaredSize: Long, name: String): ByteArray {
        if (declaredSize > MAX_FILE_BYTES) {
            throw CustomToolPackageException("Archive entry '$name' is too large")
        }
        val initial = declaredSize.plus(1).coerceIn(64, MAX_FILE_BYTES).toInt()
        val output = ByteArrayOutputStream(initial)
        val buffer = ByteArray(8 * 1024)
        var written = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            written += read
            if (written > MAX_FILE_BYTES) {
                throw CustomToolPackageException("Archive entry '$name' is too large")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun parseManifest(text: String): CustomToolManifest {
        if (text.length > MAX_MANIFEST_CHARS) {
            throw CustomToolPackageException("Manifest is too large")
        }
        return parseManifestJson(json, text)
    }

    companion object {
        /** Hard caps protecting the import path from pathological archives. */
        const val MAX_ENTRIES = 16
        const val MAX_FILE_BYTES = 1L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 2L * 1024 * 1024
        const val MAX_MANIFEST_CHARS = 256 * 1024
    }
}

/**
 * Parses a manifest JSON document; shared by the package reader and the
 * on-disk store. Throws [CustomToolPackageException] on malformed input.
 */
internal fun parseManifestJson(json: Json, text: String): CustomToolManifest {
    val element = runCatching { json.parseToJsonElement(text) }.getOrElse {
        throw CustomToolPackageException("Manifest is not valid JSON: ${it.message}")
    }
    val obj = element as? JsonObject
        ?: throw CustomToolPackageException("Manifest must be a JSON object")
    return runCatching { json.decodeFromJsonElement(CustomToolManifest.serializer(), obj) }
        .getOrElse {
            throw CustomToolPackageException("Manifest structure is invalid: ${it.message}")
        }
}
