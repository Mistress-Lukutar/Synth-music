package com.synth.synthmusic.domain.usecase

import android.content.Context
import android.os.Environment
import com.synth.synthmusic.domain.model.Playlist
import com.synth.synthmusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Use case for exporting a playlist to an M3U file.
 *
 * The M3U file is written to the public Music/Playlists directory
 * or the app's external files directory as a fallback.
 *
 * @param context application context for resolving file paths.
 * @param playlistRepository the repository providing playlist song data.
 */
class ExportPlaylistUseCase(
    private val context: Context,
    private val playlistRepository: PlaylistRepository
) {

    /**
     * Exports the given playlist to an M3U8 file.
     *
     * @param playlist the playlist to export.
     * @return the exported file on success, or null on failure.
     */
    suspend operator fun invoke(playlist: Playlist): File? = withContext(Dispatchers.IO) {
        runCatching {
            val songs = playlistRepository.observePlaylistSongs(playlist.id).first()
            val fileName = "${playlist.name.replace(" ", "_")}.m3u8"
            val outputDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "Playlists"
            ).also { it.mkdirs() }

            val outputFile = File(outputDir, fileName)
            outputFile.bufferedWriter().use { writer ->
                writer.write("#EXTM3U\n")
                songs.forEach { song ->
                    writer.write("#EXTINF:${song.durationMs / 1000},${song.artist} - ${song.title}\n")
                    writer.write("${song.path}\n")
                }
            }
            outputFile
        }.getOrNull()
    }
}
