package com.synth.synthmusic.data.ai.tools

import android.content.Context
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.ai.AiTool
import com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

private const val ILLEGAL_FILENAME_CHARS = "\\/:*?\"<>|"
private const val MAX_FILES_PER_CALL = 50

/**
 * `list_music_folders` — the only legal move targets for `move_file`.
 */
class ListMusicFoldersTool(
    private val songRepository: SongRepository
) : AiTool {

    override val name = "list_music_folders"
    override val description = "List the music folders known to the library. move_file may " +
        "only move songs into one of these existing folders."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { }
    }
    override val requiredGrants = setOf(AiCapability.READ_LIBRARY)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val folders = songRepository.observeFolders().first()
        return ToolOutcome(
            buildJsonObject {
                put("count", folders.size)
                put(
                    "folders",
                    kotlinx.serialization.json.buildJsonArray {
                        folders.forEach { add(JsonPrimitive(it)) }
                    }
                )
            }.toString()
        )
    }
}

/**
 * `rename_file` — renames one MP3 in its existing directory and updates the
 * library + MediaStore.
 */
class RenameFileTool(
    private val appContext: Context,
    private val songRepository: SongRepository
) : AiTool {

    override val name = "rename_file"
    override val description = "Rename a song's MP3 file. Provide songId (never a raw path) " +
        "and the new file name; the .mp3 extension is kept and illegal characters are stripped."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("songId", buildJsonObject { put("type", "string") })
            put("newName", ToolSchemas.queryProperty("New file name (without directory)"))
        }
        putJsonArray("required") {
            add(JsonPrimitive("songId")); add(JsonPrimitive("newName"))
        }
    }
    override val requiredGrants = setOf(AiCapability.MANAGE_FILES)
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override fun summarize(args: JsonObject): String {
        val songId = args["songId"]?.jsonPrimitive?.content ?: "?"
        val newName = args["newName"]?.jsonPrimitive?.content ?: "?"
        return "Rename file of song $songId to '$newName'"
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome =
        withContext(Dispatchers.IO) {
            val songId = args["songId"]?.jsonPrimitive?.content
                ?: return@withContext ToolOutcome("Missing songId", isError = true)
            val rawName = args["newName"]?.jsonPrimitive?.content
                ?: return@withContext ToolOutcome("Missing newName", isError = true)
            val song = songRepository.getSongById(songId)
                ?: return@withContext ToolOutcome("Unknown song id: $songId", isError = true)

            val sanitized = rawName.filter { it !in ILLEGAL_FILENAME_CHARS }.trim()
            val newName = if (sanitized.endsWith(".mp3", ignoreCase = true)) {
                sanitized
            } else {
                "$sanitized.mp3"
            }
            if (newName.isBlank() || newName == ".mp3") {
                return@withContext ToolOutcome("Invalid new name", isError = true)
            }
            val source = File(song.path)
            if (!source.exists()) {
                return@withContext ToolOutcome("File not found: ${song.path}", isError = true)
            }
            val target = File(source.parentFile, newName)
            if (target.exists()) {
                return@withContext ToolOutcome("Target name already exists", isError = true)
            }
            if (!source.renameTo(target)) {
                return@withContext ToolOutcome("renameTo failed (file locked?)", isError = true)
            }
            val updated = song.copy(path = target.absolutePath, id = song.id)
            songRepository.saveSongs(listOf(updated))
            scanFiles(appContext, listOf(target.absolutePath, source.absolutePath))
            ToolOutcome("Renamed to ${target.name}")
        }
}

/**
 * `move_file` — moves songs into an existing music folder
 * (copy + verify + delete original).
 */
class MoveFileTool(
    private val appContext: Context,
    private val songRepository: SongRepository
) : AiTool {

    override val name = "move_file"
    override val description = "Move up to 50 songs into an existing music folder obtained " +
        "from list_music_folders. Uses copy + verify + delete; updates the library."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("songIds") {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
                put("description", "Song ids to move (max 50)")
            }
            put("targetFolder", ToolSchemas.queryProperty("Destination folder from list_music_folders"))
        }
        putJsonArray("required") {
            add(JsonPrimitive("songIds")); add(JsonPrimitive("targetFolder"))
        }
    }
    override val requiredGrants = setOf(AiCapability.MANAGE_FILES)
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override fun summarize(args: JsonObject): String {
        val ids = stringArray(args, "songIds")
        val target = args["targetFolder"]?.jsonPrimitive?.content ?: "?"
        return "Move ${ids.size} file(s) to $target"
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome =
        withContext(Dispatchers.IO) {
            val songIds = stringArray(args, "songIds")
            val targetFolder = args["targetFolder"]?.jsonPrimitive?.content
                ?: return@withContext ToolOutcome("Missing targetFolder", isError = true)
            if (songIds.isEmpty() || songIds.size > MAX_FILES_PER_CALL) {
                return@withContext ToolOutcome(
                    "songIds must contain 1..$MAX_FILES_PER_CALL ids", isError = true
                )
            }
            val knownFolders = songRepository.observeFolders().first()
            val target = File(targetFolder)
            // File-op rule: only existing, known music folders are legal targets.
            if (!target.isDirectory || targetFolder !in knownFolders) {
                return@withContext ToolOutcome(
                    "Target must be an existing music folder from list_music_folders",
                    isError = true
                )
            }
            val failures = mutableListOf<String>()
            var moved = 0
            val scanned = mutableListOf<String>()
            for (songId in songIds) {
                val song = songRepository.getSongById(songId)
                if (song == null) {
                    failures.add("$songId: unknown id")
                    continue
                }
                val source = File(song.path)
                if (!source.exists()) {
                    failures.add("${song.title}: file missing")
                    continue
                }
                if (source.parentFile?.canonicalPath == target.canonicalPath) {
                    failures.add("${song.title}: already in target")
                    continue
                }
                val targetFile = File(target, source.name)
                if (targetFile.exists()) {
                    failures.add("${song.title}: name exists in target")
                    continue
                }
                source.copyTo(targetFile, overwrite = false)
                if (targetFile.length() != source.length()) {
                    targetFile.delete()
                    failures.add("${song.title}: copy verification failed")
                    continue
                }
                if (!source.delete()) {
                    targetFile.delete()
                    failures.add("${song.title}: could not delete original")
                    continue
                }
                songRepository.saveSongs(
                    listOf(song.copy(path = targetFile.absolutePath))
                )
                scanned.add(targetFile.absolutePath)
                scanned.add(source.absolutePath)
                moved++
            }
            scanFiles(appContext, scanned)
            ToolOutcome(
                buildJsonObject {
                    put("moved", moved)
                    put("failed", failures.size)
                    if (failures.isNotEmpty()) {
                        put(
                            "failures",
                            kotlinx.serialization.json.buildJsonArray {
                                failures.forEach { add(JsonPrimitive(it)) }
                            }
                        )
                    }
                }.toString(),
                isError = moved == 0 && failures.isNotEmpty()
            )
        }
}

/**
 * `delete_file` — trash-based deletion. Never calls File.delete() on the
 * user's library; files land in the SynthMusic trash and can be restored.
 */
class DeleteFileTool(
    private val appContext: Context,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val trashManager: TrashManager
) : AiTool {

    override val name = "delete_file"
    override val description = "Move up to 50 songs to the SynthMusic trash (recoverable in " +
        "AI Settings). Removes the library entries and playlist references. This is never an " +
        "irreversible delete."
    override val paramsSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("songIds") {
                put("type", "array")
                put("items", buildJsonObject { put("type", "string") })
                put("description", "Song ids to trash (max 50)")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("songIds")) }
    }
    override val requiredGrants = setOf(AiCapability.MANAGE_FILES)
    override val confirmationLevel = ConfirmationLevel.ALWAYS_CONFIRM

    override fun summarize(args: JsonObject): String {
        val count = stringArray(args, "songIds").size
        return "Trash $count file(s) (recoverable, never irreversible)"
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome =
        withContext(Dispatchers.IO) {
            val songIds = stringArray(args, "songIds")
            if (songIds.isEmpty() || songIds.size > MAX_FILES_PER_CALL) {
                return@withContext ToolOutcome(
                    "songIds must contain 1..$MAX_FILES_PER_CALL ids", isError = true
                )
            }
            val failures = mutableListOf<String>()
            var trashed = 0
            val scanned = mutableListOf<String>()
            for (songId in songIds) {
                val song = songRepository.getSongById(songId)
                if (song == null) {
                    failures.add("$songId: unknown id")
                    continue
                }
                val file = File(song.path)
                if (file.exists()) {
                    trashManager.moveToTrash(file, song.id)
                    scanned.add(file.absolutePath)
                }
                // Cascades: library row, playlist references.
                songRepository.deleteSong(song.id)
                trashed++
            }
            scanFiles(appContext, scanned)
            ToolOutcome(
                buildJsonObject {
                    put("trashed", trashed)
                    put("failed", failures.size)
                    put("trash_path", trashManager.trashPath())
                    if (failures.isNotEmpty()) {
                        put(
                            "failures",
                            kotlinx.serialization.json.buildJsonArray {
                                failures.forEach { add(JsonPrimitive(it)) }
                            }
                        )
                    }
                }.toString(),
                isError = trashed == 0 && failures.isNotEmpty()
            )
        }
}
