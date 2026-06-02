package com.synth.synthmusic.data.local.cover

import android.content.Context
import java.io.File

/**
 * Manages local file-system caching of artwork images.
 *
 * Song artwork (extracted from MP3 or MediaStore) is stored in the app cache dir.
 * User-assigned covers for albums, playlists, and artists are stored in the app files dir
 * so they survive cache eviction.
 */
class CoverCache(private val context: Context) {

    enum class Type(val folderName: String) {
        SONG("songs"),
        ALBUM("albums"),
        PLAYLIST("playlists"),
        ARTIST("artists")
    }

    /**
     * Saves extracted artwork for a song into the app cache.
     * Returns the [File] where the artwork was written.
     */
    fun saveSongArtwork(songId: String, bytes: ByteArray): File {
        val file = File(getSongArtworkDir(), "$songId.jpg")
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    /**
     * Saves a user-picked custom cover for an album, playlist, or artist.
     * Stored in [filesDir] so it persists across cache clears.
     */
    fun saveCustomCover(type: Type, id: String, bytes: ByteArray): File {
        val file = File(getCustomCoverDir(type), "$id.jpg")
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    /**
     * Returns the [File] for the given cover if it exists, otherwise null.
     */
    fun getCoverFile(type: Type, id: String): File? {
        val file = when (type) {
            Type.SONG -> File(getSongArtworkDir(), "$id.jpg")
            else -> File(getCustomCoverDir(type), "$id.jpg")
        }
        return if (file.exists()) file else null
    }

    /**
     * Deletes the cover file for the given type and id.
     */
    fun deleteCover(type: Type, id: String) {
        val file = when (type) {
            Type.SONG -> File(getSongArtworkDir(), "$id.jpg")
            else -> File(getCustomCoverDir(type), "$id.jpg")
        }
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Clears all cached artwork (songs and custom covers).
     */
    fun clearAll() {
        getSongArtworkDir().deleteRecursively()
        Type.entries.forEach { type ->
            getCustomCoverDir(type).deleteRecursively()
        }
    }

    private fun getSongArtworkDir(): File =
        File(context.cacheDir, "artworks/songs")

    private fun getCustomCoverDir(type: Type): File =
        File(context.filesDir, "covers/${type.folderName}")
}
