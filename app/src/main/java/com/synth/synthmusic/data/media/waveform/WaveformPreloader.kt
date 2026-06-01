package com.synth.synthmusic.data.media.waveform

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
            for (song in songs) {
                ensureActive()
                try {
                    val cached = waveformDataDao.getBySongId(song.id)
                    if (cached != null) continue

                    val amplitudes = waveformGenerator.generate(song.uri, bars = 200)
                    if (amplitudes.isNotEmpty()) {
                        waveformDataDao.insert(
                            WaveformDataEntity(
                                songId = song.id,
                                amplitudes = amplitudes.toList()
                            )
                        )
                    }
                } catch (_: Exception) {
                    // Ignore per-track failures so the batch continues
                }
                delay(50) // yield CPU between tracks
            }
        }
    }
}
