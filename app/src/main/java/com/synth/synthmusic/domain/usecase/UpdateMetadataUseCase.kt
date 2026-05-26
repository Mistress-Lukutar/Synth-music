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
 * updates the local Room database record.
 *
 * @param songRepository the repository for persisting song data.
 */
class UpdateMetadataUseCase(
    private val songRepository: SongRepository
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
        lyrics: String? = null
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(song.path)
            if (file.exists()) {
                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tagOrCreateAndSetDefault
                title?.let { tag.setField(FieldKey.TITLE, it) }
                artist?.let { tag.setField(FieldKey.ARTIST, it) }
                album?.let { tag.setField(FieldKey.ALBUM, it) }
                albumArtist?.let { tag.setField(FieldKey.ALBUM_ARTIST, it) }
                genre?.let { tag.setField(FieldKey.GENRE, it) }
                year?.let { tag.setField(FieldKey.YEAR, it) }
                trackNumber?.let { tag.setField(FieldKey.TRACK, it) }
                comment?.let { tag.setField(FieldKey.COMMENT, it) }
                AudioFileIO.write(audioFile)
            }
        }

        val updated = song.copy(
            title = title ?: song.title,
            artist = artist ?: song.artist,
            album = album ?: song.album,
            albumArtist = albumArtist ?: song.albumArtist,
            genre = genre ?: song.genre,
            year = year?.toIntOrNull() ?: song.year,
            trackNumber = trackNumber?.toIntOrNull() ?: song.trackNumber,
            comment = comment ?: song.comment,
            lyrics = lyrics ?: song.lyrics
        )
        songRepository.saveSongs(listOf(updated))
    }
}
