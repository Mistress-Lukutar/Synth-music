package com.synth.synthmusic.domain.usecase

import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * Use case for updating ID3 metadata of a single song.
 *
 * Writes changes to the underlying MP3 file via JAudioTagger and
 * updates the local Room database record. When the file exists but the tag
 * write fails, nothing is persisted so the database never diverges from the
 * file (a divergence would be reverted by the next library scan).
 * When the file is missing, only the Room record is updated.
 *
 * @param songRepository the repository for persisting song data.
 */
class UpdateMetadataUseCase(
    private val songRepository: SongRepository,
    private val writeArtworkUseCase: WriteArtworkToMp3UseCase
) {

    /**
     * Updates metadata fields for a single song.
     *
     * Fields with a null value are skipped and not written.
     *
     * @param song the song to update.
     * @param title optional new title.
     * @param artist optional new artist.
     * @param album optional new album.
     * @param albumArtist optional new album artist.
     * @param genre optional new genre.
     * @param year optional new year.
     * @param trackNumber optional new track number.
     * @param comment optional new comment.
     * @param lyrics optional new lyrics.
     * @param writeLyricsToTag when true, [lyrics] is also written to the ID3
     * USLT tag instead of only the Room record.
     * @param artworkBytes optional new artwork image bytes to write into the MP3 file.
     */
    suspend operator fun invoke(
        song: Song,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        albumArtist: String? = null,
        genre: String? = null,
        year: String? = null,
        trackNumber: String? = null,
        comment: String? = null,
        lyrics: String? = null,
        writeLyricsToTag: Boolean = false,
        artworkBytes: ByteArray? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File(song.path)
        val tagWrite = runCatching {
            if (file.exists()) {
                // Write to a temp copy in the same directory, then replace the
                // original — in-place writes risk corrupting the MP3 on crash.
                // The temp name must keep the real audio extension: JAudioTagger
                // selects its reader/writer by extension and rejects unknown ones.
                // The leading dot hides the temp from MediaStore, so a copy left
                // behind by a killed process is never indexed as a track.
                val extension = file.extension.ifBlank { "mp3" }
                val tempFile = File(file.parentFile, ".${file.nameWithoutExtension}.synthtmp.$extension")
                try {
                    file.copyTo(tempFile, overwrite = true)
                    val audioFile = AudioFileIO.read(tempFile)
                    val tag = audioFile.tagOrCreateAndSetDefault
                    title?.let { tag.setField(FieldKey.TITLE, it) }
                    artist?.let { tag.setField(FieldKey.ARTIST, it) }
                    album?.let { tag.setField(FieldKey.ALBUM, it) }
                    albumArtist?.let { tag.setField(FieldKey.ALBUM_ARTIST, it) }
                    genre?.let { tag.setField(FieldKey.GENRE, it) }
                    year?.let { tag.setField(FieldKey.YEAR, it) }
                    trackNumber?.let { tag.setField(FieldKey.TRACK, it) }
                    comment?.let { tag.setField(FieldKey.COMMENT, it) }
                    if (writeLyricsToTag) {
                        lyrics?.let { tag.setField(FieldKey.LYRICS, it) }
                    }
                    AudioFileIO.write(audioFile)
                    if (!tempFile.renameTo(file)) {
                        // Cross-device or lock fallback: copy over the original.
                        file.delete()
                        if (!tempFile.renameTo(file)) {
                            tempFile.copyTo(file, overwrite = true)
                            tempFile.delete()
                        }
                    }
                } finally {
                    tempFile.delete()
                }
            }
        }
        // Never persist to the database when the file write failed: the next scan
        // would otherwise overwrite the DB rows with the stale file tags again.
        if (tagWrite.isFailure) {
            return@withContext Result.failure(
                tagWrite.exceptionOrNull() ?: IllegalStateException("Metadata write failed")
            )
        }

        if (artworkBytes != null) {
            writeArtworkUseCase(song, artworkBytes).onFailure {
                return@withContext Result.failure<Unit>(it)
            }
        }

        val currentArtworkUri = songRepository.getSongById(song.id)?.artworkUri
        val updated = song.copy(
            title = title ?: song.title,
            artist = artist ?: song.artist,
            album = album ?: song.album,
            albumArtist = albumArtist ?: song.albumArtist,
            genre = genre ?: song.genre,
            year = year?.toIntOrNull() ?: song.year,
            trackNumber = trackNumber?.toIntOrNull() ?: song.trackNumber,
            comment = comment ?: song.comment,
            lyrics = lyrics ?: song.lyrics,
            artworkUri = currentArtworkUri ?: song.artworkUri,
            // Refresh size/date so the next library scan recognizes the file as
            // up-to-date instead of re-extracting possibly stale MediaStore data.
            dateModified = if (file.exists()) (file.lastModified() / 1000) * 1000 else song.dateModified,
            fileSize = if (file.exists()) file.length() else song.fileSize
        )
        songRepository.saveSongs(listOf(updated))
        Result.success(Unit)
    }
}
