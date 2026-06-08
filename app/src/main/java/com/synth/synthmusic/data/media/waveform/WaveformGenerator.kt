package com.synth.synthmusic.data.media.waveform

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Generates a down-sampled amplitude envelope (waveform) for an audio file
 * using [MediaExtractor] + [MediaCodec].
 *
 * The implementation decodes the track once in a single pass and accumulates
 * per-bar RMS values on the fly. This avoids the seek/flush cycle that caused
 * excessive MediaCodec logging and occasional infinite loops in the previous
 * sparse-chunk approach.
 *
 * Operates on a background thread.
 *
 * @param context Application context.
 */
class WaveformGenerator(
    private val context: Context
) {

    /**
     * Decode the audio track at [uri] and return a list of normalized amplitudes
     * in the range [0, 1]. The resulting array contains [bars] elements representing
     * the track's loudness envelope.
     */
    suspend fun generate(uri: String, bars: Int = 200): FloatArray = withContext(Dispatchers.IO) {
        val startTime = SystemClock.elapsedRealtime()

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri.toUri(), null)
        } catch (e: Exception) {
            extractor.release()
            return@withContext defaultWaveform(bars)
        }

        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            return@withContext defaultWaveform(bars)
        }

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: run {
            extractor.release()
            return@withContext defaultWaveform(bars)
        }

        val durationUs = readDurationUs(format, uri)
        if (durationUs <= 0) {
            extractor.release()
            return@withContext defaultWaveform(bars)
        }

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            extractor.release()
            return@withContext defaultWaveform(bars)
        }

        codec.configure(format, null, null, 0)

        val bufferInfo = MediaCodec.BufferInfo()
        val sums = DoubleArray(bars)
        val counts = IntArray(bars)
        var sampleRate = 0
        var channelCount = 1
        var inputDone = false
        var outputDone = false

        try {
            codec.start()

            while (!outputDone) {
                ensureActive()

                if (!inputDone) {
                    val inputBufferId = codec.dequeueInputBuffer(TIMEOUT_US)
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
                            codec.queueInputBuffer(
                                inputBufferId, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputBufferId >= 0 -> {
                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferId) ?: continue
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)
                            outputBuffer.clear()

                            if (sampleRate > 0) {
                                accumulateSamples(
                                    pcmBytes = chunk,
                                    presentationTimeUs = bufferInfo.presentationTimeUs,
                                    durationUs = durationUs,
                                    channelCount = channelCount,
                                    bars = bars,
                                    sums = sums,
                                    counts = counts
                                )
                            }
                        }
                        codec.releaseOutputBuffer(outputBufferId, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }

                    outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, 0)
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        val result = FloatArray(bars) { index ->
            val count = counts[index]
            if (count == 0) {
                0.05f
            } else {
                kotlin.math.sqrt(sums[index] / count).toFloat().coerceIn(0.01f, 1f)
            }
        }

        val elapsed = SystemClock.elapsedRealtime() - startTime
        Log.d(TAG, "Waveform generated in ${elapsed}ms for $uri")
        result
    }

    /**
     * Reads the track duration in microseconds, preferring the format value and
     * falling back to [MediaMetadataRetriever].
     */
    private fun readDurationUs(format: MediaFormat, uri: String): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            format.getLong(MediaFormat.KEY_DURATION, -1)
        } else {
            -1
        }.takeIf { it > 0 } ?: run {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri.toUri())
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.times(1000L) ?: -1
            } catch (_: Exception) {
                -1
            } finally {
                retriever.release()
            }
        }
    }

    /**
     * Accumulates decoded PCM samples into the per-bar RMS buckets.
     *
     * The mapping uses presentation time so that the result is independent of
     * the output sample rate.
     */
    private fun accumulateSamples(
        pcmBytes: ByteArray,
        presentationTimeUs: Long,
        durationUs: Long,
        channelCount: Int,
        bars: Int,
        sums: DoubleArray,
        counts: IntArray
    ) {
        // We assume 16-bit PCM output, which is the default for MediaCodec audio decoders.
        val frameSize = channelCount * 2
        val frames = pcmBytes.size / frameSize
        if (frames == 0) return

        val stepUs = durationUs / bars.toDouble()
        val frameDurationUs = stepUs / (frames.coerceAtLeast(1))

        for (frameIndex in 0 until frames) {
            val frameTimeUs = presentationTimeUs + (frameIndex * frameDurationUs).toLong()
            val barIndex = ((frameTimeUs / stepUs).toInt()).coerceIn(0, bars - 1)

            // Average all channels for this frame.
            val offset = frameIndex * frameSize
            var sampleSum = 0
            for (ch in 0 until channelCount) {
                val sampleOffset = offset + ch * 2
                val sample = (pcmBytes[sampleOffset].toInt() and 0xFF) or
                        (pcmBytes[sampleOffset + 1].toInt() shl 8)
                sampleSum += sample
            }
            val averaged = sampleSum / channelCount.toFloat()
            val normalized = kotlin.math.abs(averaged / 32768f)

            sums[barIndex] += (normalized * normalized).toDouble()
            counts[barIndex]++
        }
    }

    private fun defaultWaveform(bars: Int): FloatArray = FloatArray(bars) { 0.05f }

    companion object {
        private const val TAG = "WaveformGenerator"
        private const val TIMEOUT_US = 10_000L
    }
}
