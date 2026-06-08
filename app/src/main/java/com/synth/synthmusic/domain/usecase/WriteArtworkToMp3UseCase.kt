package com.synth.synthmusic.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.synth.synthmusic.data.local.cover.CoverCache
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.images.StandardArtwork
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Writes a new artwork image into an MP3 file's ID3 tags and updates the local cache.
 *
 * The image is down-sampled to a maximum dimension of 1200px and compressed as JPEG
 * to avoid bloating the audio file.
 */
class WriteArtworkToMp3UseCase(
    private val songRepository: SongRepository,
    private val coverCache: CoverCache
) {

    suspend operator fun invoke(song: Song, imageBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val scaledBytes = scaleAndCompress(imageBytes)
            val audioFile = AudioFileIO.read(File(song.path))
            val tag = audioFile.tagOrCreateAndSetDefault

            tag.deleteArtworkField()
            val artwork = StandardArtwork()
            artwork.binaryData = scaledBytes
            artwork.mimeType = "image/jpeg"
            artwork.pictureType = org.jaudiotagger.tag.reference.PictureTypes.DEFAULT_ID
            tag.setField(artwork)

            AudioFileIO.write(audioFile)

            // Update local cache so the UI picks it up immediately
            val coverFile = coverCache.saveSongArtwork(song.id, scaledBytes)
            songRepository.updateSongArtwork(song.id, Uri.fromFile(coverFile).toString())
        }
    }

    /**
     * Removes the artwork field from the MP3 file and clears the local cache entry.
     */
    suspend fun removeArtwork(song: Song): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val audioFile = AudioFileIO.read(File(song.path))
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.deleteArtworkField()
            AudioFileIO.write(audioFile)

            coverCache.deleteCover(CoverCache.Type.SONG, song.id)
            songRepository.updateSongArtwork(song.id, null)
        }
    }

    /**
     * Re-extracts the embedded artwork from the MP3 file, updates cache and DB.
     */
    suspend fun resetToEmbedded(song: Song): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(song.path)
                val bytes = retriever.embeddedPicture
                if (bytes != null) {
                    val coverFile = coverCache.saveSongArtwork(song.id, bytes)
                    songRepository.updateSongArtwork(song.id, Uri.fromFile(coverFile).toString())
                } else {
                    coverCache.deleteCover(CoverCache.Type.SONG, song.id)
                    songRepository.updateSongArtwork(song.id, null)
                }
            } finally {
                retriever.release()
            }
        }
    }

    private fun scaleAndCompress(bytes: ByteArray): ByteArray {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        val maxDimension = 1200
        var inSampleSize = 1
        while (options.outWidth / inSampleSize > maxDimension ||
            options.outHeight / inSampleSize > maxDimension
        ) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: return bytes

        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }
    }
}
