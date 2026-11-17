package com.synth.synthmusic.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.synth.synthmusic.data.local.cover.CoverCache
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.images.StandardArtwork
import org.jaudiotagger.tag.reference.PictureTypes
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentFieldKey
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.tag.vorbiscomment.util.Base64Coder
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Writes a new artwork image into an audio file's tags (ID3 for MP3, FLAC/Ogg
 * Vorbis picture blocks otherwise) and updates the local cache.
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
            val image = scaleAndCompress(imageBytes)
            val audioFile = AudioFileIO.read(File(song.path))
            val tag = audioFile.tagOrCreateAndSetDefault

            tag.deleteArtworkField()
            addArtworkField(tag, image)

            AudioFileIO.write(audioFile)

            // Update local cache so the UI picks it up immediately
            val coverFile = coverCache.saveSongArtwork(song.id, image.bytes)
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

    /**
     * Builds and sets the artwork field without jaudiotagger's Artwork-based path.
     *
     * That path derives picture dimensions through `javax.imageio`, which does not
     * exist on Android ("Failed resolution of: Ljavax/imageio/ImageIO"), so FLAC and
     * Ogg Vorbis picture fields are constructed directly with dimensions decoded by
     * [scaleAndCompress] instead. ID3/Mp4 tags embed the raw bytes as-is.
     */
    private fun addArtworkField(tag: Tag, image: ScaledImage) {
        when (tag) {
            is FlacTag -> tag.setField(
                tag.createArtworkField(
                    image.bytes,
                    PictureTypes.DEFAULT_ID,
                    JPEG_MIME_TYPE,
                    "",
                    image.width,
                    image.height,
                    0,
                    0
                )
            )
            is VorbisCommentTag -> {
                val picture = MetadataBlockDataPicture(
                    image.bytes,
                    PictureTypes.DEFAULT_ID,
                    JPEG_MIME_TYPE,
                    "",
                    image.width,
                    image.height,
                    0,
                    0
                )
                tag.setField(
                    tag.createField(
                        VorbisCommentFieldKey.METADATA_BLOCK_PICTURE,
                        String(Base64Coder.encode(picture.rawContent))
                    )
                )
            }
            else -> tag.setField(StandardArtwork().apply {
                binaryData = image.bytes
                mimeType = JPEG_MIME_TYPE
                pictureType = PictureTypes.DEFAULT_ID
            })
        }
    }

    /**
     * Down-samples to a maximum dimension of 1200px and compresses as JPEG,
     * keeping the original pixel dimensions for picture metadata fields.
     */
    private fun scaleAndCompress(bytes: ByteArray): ScaledImage {
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
            ?: return ScaledImage(
                bytes,
                options.outWidth.coerceAtLeast(0),
                options.outHeight.coerceAtLeast(0)
            )

        val compressedBytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }
        val width = if (options.outWidth > 0) options.outWidth else bitmap.width
        val height = if (options.outHeight > 0) options.outHeight else bitmap.height
        return ScaledImage(compressedBytes, width, height)
    }

    /** Re-encoded image bytes paired with the source picture dimensions. */
    private data class ScaledImage(val bytes: ByteArray, val width: Int, val height: Int)

    private companion object {
        const val JPEG_MIME_TYPE = "image/jpeg"
    }
}
