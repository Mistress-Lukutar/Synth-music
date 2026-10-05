package com.synth.synthmusic.data.ai.tools

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * One entry of the AI trash: the moved file plus a manifest with its
 * original location so restore can put it back.
 */
@Serializable
data class TrashEntry(
    val fileName: String,
    val originalPath: String,
    val songId: String,
    val trashedAt: Long
)

/**
 * Trash-based deletion for AI file operations. Files are moved (never
 * deleted) into `<primaryMusicStorage>/SynthMusic/.trash/` together with a
 * small JSON manifest; restore moves them back. Same-volume moves are cheap
 * renames; cross-volume falls back to copy+delete.
 */
class TrashManager(
    context: Context,
    private val json: Json
) {

    private val trashDir: File = run {
        val primary = Environment.getExternalStorageDirectory()
        val base = primary ?: context.getExternalFilesDir(null)?.parentFile ?: context.filesDir
        File(base, "SynthMusic/.trash").apply { mkdirs() }
    }

    /** Returns the trash directory path (for UI display). */
    fun trashPath(): String = trashDir.absolutePath

    /**
     * Moves [source] into the trash on behalf of [songId].
     *
     * @return the resulting [TrashEntry].
     */
    suspend fun moveToTrash(source: File, songId: String): TrashEntry = withContext(Dispatchers.IO) {
        val fileName = "${UUID.randomUUID()}_${source.name}"
        val target = File(trashDir, fileName)
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        val entry = TrashEntry(
            fileName = fileName,
            originalPath = source.absolutePath,
            songId = songId,
            trashedAt = System.currentTimeMillis()
        )
        File(trashDir, "$fileName.json").writeText(json.encodeToString(TrashEntry.serializer(), entry))
        entry
    }

    /**
     * Lists all trash entries (manifest-based; orphans ignored).
     */
    suspend fun list(): List<TrashEntry> = withContext(Dispatchers.IO) {
        trashDir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { manifest ->
                runCatching {
                    json.decodeFromString(TrashEntry.serializer(), manifest.readText())
                }.getOrNull()
            }
            .orEmpty()
            .sortedByDescending { it.trashedAt }
    }

    /**
     * Restores an entry to its original location (creating parent dirs).
     *
     * @return true when the file is back at its original path.
     */
    suspend fun restore(entry: TrashEntry): Boolean = withContext(Dispatchers.IO) {
        val file = File(trashDir, entry.fileName)
        if (!file.exists()) return@withContext false
        val original = File(entry.originalPath)
        original.parentFile?.mkdirs()
        val moved = file.renameTo(original) || run {
            file.copyTo(original, overwrite = true)
            file.delete()
            true
        }
        if (moved) File(trashDir, "${entry.fileName}.json").delete()
        moved
    }

    /**
     * Permanently removes one entry (file + manifest).
     */
    suspend fun purge(entry: TrashEntry): Boolean = withContext(Dispatchers.IO) {
        File(trashDir, entry.fileName).delete() &&
            File(trashDir, "${entry.fileName}.json").delete()
    }

    /**
     * Empties the whole trash.
     */
    suspend fun empty(): Int = withContext(Dispatchers.IO) {
        val entries = list()
        entries.forEach { entry -> purge(entry) }
        entries.size
    }
}
