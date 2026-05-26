package com.synth.synthmusic.data.media

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.synth.synthmusic.data.local.database.EqPresetDao
import com.synth.synthmusic.data.local.database.EqPresetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Manager for system audio effects (equalizer, bass boost, loudness).
 */
class AudioEffectsManager(
    private val eqPresetDao: EqPresetDao
) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null

    var isEqEnabled: Boolean = false
        private set

    val presets: Flow<List<Pair<Long, String>>> = eqPresetDao.observeAll().map { list ->
        list.map { it.id to it.name }
    }

    fun initWithSession(audioSessionId: Int) {
        release()
        equalizer = Equalizer(0, audioSessionId)
        bassBoost = BassBoost(0, audioSessionId)
        loudness = LoudnessEnhancer(audioSessionId)
        equalizer?.enabled = isEqEnabled
        bassBoost?.enabled = isEqEnabled
        loudness?.enabled = isEqEnabled
    }

    fun setEnabled(enabled: Boolean) {
        isEqEnabled = enabled
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        loudness?.enabled = enabled
    }

    fun getBandLevelRange(): Pair<Short, Short> {
        val eq = equalizer ?: return Pair(-1500, 1500)
        return Pair(eq.bandLevelRange[0], eq.bandLevelRange[1])
    }

    fun getBandLevel(band: Short): Short {
        return equalizer?.getBandLevel(band) ?: 0
    }

    fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
    }

    fun getCenterFreq(band: Short): Int {
        return equalizer?.getCenterFreq(band)?.div(1000) ?: 0
    }

    fun getNumberOfBands(): Short {
        return equalizer?.numberOfBands ?: 5
    }

    fun setBassBoost(strength: Short) {
        bassBoost?.setStrength(strength)
    }

    fun getBassBoost(): Short {
        return bassBoost?.roundedStrength ?: 0
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        loudness?.enabled = enabled && isEqEnabled
    }

    suspend fun savePreset(name: String, bandValues: List<Int>) {
        withContext(Dispatchers.IO) {
            eqPresetDao.insert(
                EqPresetEntity(name = name, bandValues = bandValues.joinToString(","))
            )
        }
    }

    suspend fun loadPreset(presetId: Long) {
        withContext(Dispatchers.IO) {
            val entity = eqPresetDao.getById(presetId) ?: return@withContext
            val values = entity.bandValues.split(",").map { it.toInt() }
            values.forEachIndexed { index, value ->
                setBandLevel(index.toShort(), value.toShort())
            }
        }
    }

    fun release() {
        equalizer?.release()
        bassBoost?.release()
        loudness?.release()
        equalizer = null
        bassBoost = null
        loudness = null
    }
}
