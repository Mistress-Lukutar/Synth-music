package com.synth.synthmusic.data.media.waveform

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Generates a down-sampled amplitude envelope (waveform) for an audio file
 * using MediaExtractor + MediaCodec.
 *
 * Operates on a background thread. Instead of decoding the entire file,
 * it decodes ~200 short chunks (~100 ms each) at evenly spaced positions,
 * which is 5–30× faster than a full decode depending on track length.
 *
 * @param context Application context.
 */
class WaveformGenerator(
    private val context: Context
) {

    /**
     * Decode sparse chunks of the audio track at [uri] and return a list of
     * normalized amplitudes in the range [0, 1]. The resulting array contains
     * [bars] elements representing the track's loudness envelope.
     */
    suspend fun generate(uri: String, bars: Int = 200): FloatArray = withContext(Dispatchers.IO) {
        val startTime = SystemClock.elapsedRealtime()
        Log.d(TAG, "Generating waveform for $uri ($bars bars)")

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
        val durationUs = format.getLong(MediaFormat.KEY_DURATION, -1)
        if (durationUs <= 0) return@withContext FloatArray(bars) { 0.05f }

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            extractor.release()
            return@withContext FloatArray(bars) { 0.05f }
        }

        codec.configure(format, null, null, 0)

        val bufferInfo = MediaCodec.BufferInfo()
        val result = FloatArray(bars)
        val chunkDurationUs = 100_000L // 100 ms per chunk
        val stepUs = durationUs / bars

        try {
            codec.start()

            for (barIndex in 0 until bars) {
                ensureActive()

                val targetUs = (barIndex * stepUs).coerceAtMost(durationUs - chunkDurationUs)
                extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                codec.flush()

                val chunkSamples = mutableListOf<Float>()
                var inputDone = false
                var outputDone = false
                var reachedChunkEnd = false

                while (!outputDone && chunkSamples.size < 50_000) {
                    if (!inputDone) {
                        if (reachedChunkEnd) {
                            val inputBufferId = codec.dequeueInputBuffer(10_000)
                            if (inputBufferId >= 0) {
                                codec.queueInputBuffer(
                                    inputBufferId, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            }
                        } else {
                            val inputBufferId = codec.dequeueInputBuffer(10_000)
                            if (inputBufferId >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputBufferId) ?: continue
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inputBufferId, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    val presentationTime = extractor.sampleTime
                                    codec.queueInputBuffer(
                                        inputBufferId, 0, sampleSize,
                                        presentationTime, 0
                                    )
                                    extractor.advance()
                                    if (presentationTime >= targetUs + chunkDurationUs) {
                                        reachedChunkEnd = true
                                    }
                                }
                            }
                        }
                    }

                    val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outputBufferId >= 0 -> {
                            if (bufferInfo.presentationTimeUs >= targetUs) {
                                val outputBuffer = codec.getOutputBuffer(outputBufferId) ?: continue
                                val chunk = ByteArray(bufferInfo.size)
                                outputBuffer.get(chunk)
                                outputBuffer.clear()

                                // Assuming PCM 16-bit after decoding
                                val numSamples = chunk.size / 2
                                for (i in 0 until numSamples) {
                                    val sample = (chunk[i * 2].toInt() and 0xFF) or
                                            (chunk[i * 2 + 1].toInt() shl 8)
                                    val normalized = kotlin.math.abs(sample / 32768f)
                                    chunkSamples.add(normalized)
                                }
                            }
                            codec.releaseOutputBuffer(outputBufferId, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }

                        outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            // New format available after flush
                        }
                    }
                }

                result[barIndex] = if (chunkSamples.isEmpty()) {
                    0.05f
                } else {
                    var sumSq = 0.0
                    for (sample in chunkSamples) {
                        sumSq += sample * sample
                    }
                    kotlin.math.sqrt(sumSq / chunkSamples.size).toFloat().coerceIn(0.01f, 1f)
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        val elapsed = SystemClock.elapsedRealtime() - startTime
        Log.d(TAG, "Waveform generated in ${elapsed}ms for $uri")
        result
    }

    companion object {
        private const val TAG = "WaveformGenerator"
    }
}
