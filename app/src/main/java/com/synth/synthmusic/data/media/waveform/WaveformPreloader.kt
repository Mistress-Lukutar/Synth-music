package com.synth.synthmusic.data.media.waveform

import android.util.Log
import com.synth.synthmusic.data.local.database.WaveformDataDao
import com.synth.synthmusic.data.local.database.WaveformDataEntity
import com.synth.synthmusic.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Background batch generator for song waveform data.
 *
 * Launches fire-and-forget coroutines that pre-generate waveforms after
 * library scans, keeping the scan itself fast.
 */
class WaveformPreloader(
    private val waveformGenerator: WaveformGenerator,
    private val waveformDataDao: WaveformDataDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start background preloading for the given [songs].
     *
     * Already-cached entries are skipped. Callers should not await the result.
     */
    fun preload(songs: List<Song>) {
        scope.launch {
            var generated = 0
            var skipped = 0
            var errors = 0
            Log.d(TAG, "Starting waveform preload for ${songs.size} songs")

            for ((index, song) in songs.withIndex()) {
                ensureActive()
                try {
                    val cached = waveformDataDao.getBySongId(song.id)
                    if (cached != null) {
                        skipped++
                        continue
                    }

                    val amplitudes = waveformGenerator.generate(song.uri, bars = 200)
                    if (amplitudes.isNotEmpty()) {
                        waveformDataDao.insert(
                            WaveformDataEntity(
                                songId = song.id,
                                amplitudes = amplitudes.toList()
                            )
                        )
                        generated++
                        Log.d(TAG, "Preloaded ${index + 1}/${songs.size}: ${song.title}")
                    }
                } catch (e: Exception) {
                    errors++
                    Log.w(TAG, "Failed to generate waveform for ${song.id} (${song.title})", e)
                }
                delay(50) // yield CPU between tracks
            }

            Log.d(
                TAG,
                "Waveform preload complete. Generated: $generated, Skipped: $skipped, Errors: $errors"
            )
        }
    }

    companion object {
        private const val TAG = "WaveformPreloader"
    }
}
