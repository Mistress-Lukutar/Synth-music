package com.synth.synthmusic.data.media.waveform

import android.util.Log
import com.synth.synthmusic.data.local.database.WaveformDataDao
import com.synth.synthmusic.data.local.database.WaveformDataEntity
import com.synth.synthmusic.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Background batch generator for song waveform data.
 *
 * Launches fire-and-forget coroutines that pre-generate waveforms after
 * library scans, keeping the scan itself fast. Also supports resuming
 * incomplete generation on app startup.
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
            val existingIds = waveformDataDao.getAllSongIds().toSet()
            val pending = songs.filter { it.id !in existingIds }
            if (pending.isEmpty()) {
                Log.d(TAG, "All ${songs.size} waveforms already cached, skipping preload")
                return@launch
            }

            Log.d(TAG, "Starting waveform preload for ${pending.size} songs (${songs.size - pending.size} cached)")
            processBatch(pending, "Preloaded")
        }
    }

    /**
     * Resume generation for songs that don't have a cached waveform.
     *
     * Typically called once on application startup to catch up after
     * process death or skipped background work.
     */
    fun resumeIncomplete(allSongs: List<Song>) {
        scope.launch {
            val existingIds = waveformDataDao.getAllSongIds().toSet()
            val pending = allSongs.filter { it.id !in existingIds }
            if (pending.isEmpty()) {
                Log.d(TAG, "No incomplete waveforms to resume (${allSongs.size} total)")
                return@launch
            }

            Log.d(TAG, "Resuming incomplete waveform generation for ${pending.size} songs")
            processBatch(pending, "Resumed", delayMs = 100)
        }
    }

    private suspend fun processBatch(
        pending: List<Song>,
        actionLabel: String,
        delayMs: Long = 50
    ) {
        var generated = 0
        var errors = 0

        for ((index, song) in pending.withIndex()) {
            try {
                val amplitudes = waveformGenerator.generate(song.uri, bars = 200)
                if (amplitudes.isNotEmpty()) {
                    waveformDataDao.insert(
                        WaveformDataEntity(
                            songId = song.id,
                            amplitudes = amplitudes.toList()
                        )
                    )
                    generated++
                    Log.d(TAG, "$actionLabel ${index + 1}/${pending.size}: ${song.title}")
                }
            } catch (e: Exception) {
                errors++
                Log.w(TAG, "Failed for ${song.id} (${song.title})", e)
            }
            delay(delayMs)
        }

        Log.d(TAG, "Batch complete. Generated: $generated, Errors: $errors")
    }

    companion object {
        private const val TAG = "WaveformPreloader"
    }
}
