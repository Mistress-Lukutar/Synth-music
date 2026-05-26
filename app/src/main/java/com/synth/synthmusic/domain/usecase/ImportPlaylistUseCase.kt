package com.synth.synthmusic.domain.usecase

import android.content.Context
import android.net.Uri
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Use case for importing an M3U playlist file into the local database.
 *
 * Each entry in the M3U is matched against the local song library by file path.
 * Unmatched entries are silently skipped.
 *
 * @param context application context for resolving content URIs.
 * @param songRepository the repository for matching local songs.
 * @param playlistRepository the repository for creating the imported playlist.
 */
class ImportPlaylistUseCase(
    private val context: Context,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository
) {

    /**
     * Imports an M3U file into a new playlist.
     *
     * @param uri the content URI of the M3U file to import.
     * @param name the name for the newly created playlist.
     * @return the ID of the created playlist, or null on failure.
     */
    suspend operator fun invoke(uri: Uri, name: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                it.readLines()
            } ?: return@withContext null

            val pathSet = mutableSetOf<String>()
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    pathSet.add(trimmed)
                }
            }

            val allSongs = songRepository.observeAllSongs().first()
            val songMap = allSongs.associateBy { it.path }
            val matchedSongs = pathSet.mapNotNull { songMap[it] }

            if (matchedSongs.isEmpty()) return@withContext null

            val playlistId = playlistRepository.createPlaylist(name)
            matchedSongs.forEach { song ->
                playlistRepository.addSongToPlaylist(playlistId, song.id)
            }
            playlistId
        }.getOrNull()
    }
}
