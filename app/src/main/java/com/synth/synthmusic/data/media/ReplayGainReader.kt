package com.synth.synthmusic.data.media

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import java.io.File

/**
 * Reads ReplayGain gain values (track and album) from ID3v2 TXXX frames of an audio file.
 *
 * Extraction is relatively expensive (JAudioTagger parses the file), so it is performed
 * lazily — on first playback of a track — rather than during the library scan.
 */
object ReplayGainReader {

    /**
     * Result of a ReplayGain extraction attempt.
     */
    data class ReplayGainValues(
        val trackDb: Float?,
        val albumDb: Float?
    ) {
        /** True when neither track nor album gain was found in the file. */
        val isEmpty: Boolean get() = trackDb == null && albumDb == null
    }

    /**
     * Reads ReplayGain values from the file at [path].
     * Returns an empty [ReplayGainValues] when the file has no such tags or cannot be parsed.
     */
    fun read(path: String): ReplayGainValues {
        var trackDb: Float? = null
        var albumDb: Float? = null
        try {
            val tag = AudioFileIO.read(File(path)).tag ?: return ReplayGainValues(null, null)
            tag.getFields("TXXX").forEach { field ->
                val frame = field as? AbstractID3v2Frame ?: return@forEach
                val body = frame.body
                val description = try {
                    body.getObjectValue("Description") as? String
                } catch (_: Exception) {
                    null
                }
                val text = try {
                    body.getObjectValue("Text") as? String
                } catch (_: Exception) {
                    null
                }
                when (description) {
                    "REPLAYGAIN_TRACK_GAIN" -> trackDb = text?.replace(" dB", "")?.toFloatOrNull()
                    "REPLAYGAIN_ALBUM_GAIN" -> albumDb = text?.replace(" dB", "")?.toFloatOrNull()
                }
            }
        } catch (_: Exception) {
            // ignore tag read errors
        }
        return ReplayGainValues(trackDb, albumDb)
    }
}
