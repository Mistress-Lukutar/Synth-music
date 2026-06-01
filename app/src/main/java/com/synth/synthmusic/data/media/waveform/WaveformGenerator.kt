package com.synth.synthmusic.data.media.waveform

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Generates a down-sampled amplitude envelope (waveform) for an audio file
 * using MediaExtractor + MediaCodec. Operates on a background thread.
 *
 * @param context Application context.
 */
class WaveformGenerator(
    private val context: Context
) {

    /**
     * Decode the audio track at [uri] and return a list of normalized
     * amplitudes in the range [0, 1]. The resulting array contains [bars]
     * elements representing the track's loudness envelope.
     */
    suspend fun generate(uri: String, bars: Int = 200): FloatArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
        } catch (e: Exception) {
            return@withContext FloatArray(bars) { 0.05f }
        }

        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return@withContext FloatArray(bars) { 0.05f }

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext FloatArray(bars) { 0.05f }

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            extractor.release()
            return@withContext FloatArray(bars) { 0.05f }
        }

        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val rawSamples = mutableListOf<Float>()
        var sawInputEOS = false
        var sawOutputEOS = false
        var skipCounter = 0
        val skipRate = 1000 // decode every 1000th sample to keep it fast

        try {
            while (!sawOutputEOS) {
                ensureActive()
                if (!sawInputEOS) {
                    val inputBufferId = codec.dequeueInputBuffer(10_000)
                    if (inputBufferId >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferId) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferId, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(
                                inputBufferId, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outputBufferId >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferId) ?: continue
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        outputBuffer.clear()

                        // Assuming PCM 16-bit after decoding
                        val numSamples = chunk.size / 2
                        for (i in 0 until numSamples) {
                            val sample = (chunk[i * 2].toInt() and 0xFF) or
                                    (chunk[i * 2 + 1].toInt() shl 8)
                            val normalized = sample / 32768f
                            skipCounter++
                            if (skipCounter >= skipRate) {
                                rawSamples.add(normalized)
                                skipCounter = 0
                            }
                        }

                        codec.releaseOutputBuffer(outputBufferId, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }
                    }

                    outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // New format available
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        if (rawSamples.isEmpty()) return@withContext FloatArray(bars) { 0.05f }

        // Down-sample to the requested bar count using RMS
        val result = FloatArray(bars)
        val samplesPerBar = rawSamples.size / bars
        for (i in 0 until bars) {
            val start = i * samplesPerBar
            val end = kotlin.math.min(start + samplesPerBar, rawSamples.size)
            if (end <= start) {
                result[i] = 0.05f
                continue
            }
            var sumSq = 0.0
            for (j in start until end) {
                sumSq += rawSamples[j] * rawSamples[j]
            }
            val rms = kotlin.math.sqrt(sumSq / (end - start)).toFloat()
            result[i] = rms.coerceIn(0.01f, 1f)
        }
        result
    }
}
