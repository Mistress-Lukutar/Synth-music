package com.synth.synthmusic.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.synth.synthmusic.domain.model.AccentColor
import com.synth.synthmusic.domain.model.AppSettings
import com.synth.synthmusic.domain.model.ReplayGainMode
import com.synth.synthmusic.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore-backed storage for user application settings.
 */
/**
 * SettingsDataStore class.
 */
/**
 * SettingsDataStore class.
 */
class SettingsDataStore(
    private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val CROSSFADE_DURATION = intPreferencesKey("crossfade_duration_ms")
        private val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
        private val REPLAY_GAIN_MODE = stringPreferencesKey("replay_gain_mode")
        private val AUTO_RESCAN = booleanPreferencesKey("auto_rescan")
        private val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        private val EQ_PRESET_ID = intPreferencesKey("eq_preset_id")
        private val BASS_BOOST = intPreferencesKey("bass_boost_strength")
        private val LOUDNESS_ENABLED = booleanPreferencesKey("loudness_enabled")
        private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        private val PLAYBACK_PITCH = floatPreferencesKey("playback_pitch")
        private val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[THEME]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.DARK,
            accentColor = prefs[ACCENT_COLOR]?.let { AccentColor.valueOf(it) } ?: AccentColor.YELLOW,
            crossfadeDurationMs = prefs[CROSSFADE_DURATION] ?: 5000,
            gaplessPlayback = prefs[GAPLESS_PLAYBACK] ?: true,
            replayGainMode = prefs[REPLAY_GAIN_MODE]?.let { ReplayGainMode.valueOf(it) } ?: ReplayGainMode.TRACK,
            autoRescan = prefs[AUTO_RESCAN] ?: true,
            eqEnabled = prefs[EQ_ENABLED] ?: false,
            eqPresetId = prefs[EQ_PRESET_ID]?.toLong(),
            bassBoostStrength = prefs[BASS_BOOST] ?: 0,
            loudnessEnabled = prefs[LOUDNESS_ENABLED] ?: false,
            playbackSpeed = prefs[PLAYBACK_SPEED] ?: 1.0f,
            playbackPitch = prefs[PLAYBACK_PITCH] ?: 1.0f,
            skipSilence = prefs[SKIP_SILENCE] ?: false
        )
    }

    suspend fun updateTheme(theme: ThemeMode) {
        dataStore.edit { it[THEME] = theme.name }
    }

    suspend fun updateAccentColor(color: AccentColor) {
        dataStore.edit { it[ACCENT_COLOR] = color.name }
    }

    suspend fun updateCrossfade(durationMs: Int) {
        dataStore.edit { it[CROSSFADE_DURATION] = durationMs }
    }

    suspend fun updateGapless(enabled: Boolean) {
        dataStore.edit { it[GAPLESS_PLAYBACK] = enabled }
    }

    suspend fun updateReplayGain(mode: ReplayGainMode) {
        dataStore.edit { it[REPLAY_GAIN_MODE] = mode.name }
    }

    suspend fun updateAutoRescan(enabled: Boolean) {
        dataStore.edit { it[AUTO_RESCAN] = enabled }
    }

    suspend fun updateEqEnabled(enabled: Boolean) {
        dataStore.edit { it[EQ_ENABLED] = enabled }
    }

    suspend fun updateEqPresetId(id: Long?) {
        dataStore.edit { id?.let { v -> it[EQ_PRESET_ID] = v.toInt() } ?: run { it.remove(EQ_PRESET_ID) } }
    }

    suspend fun updateBassBoost(strength: Int) {
        dataStore.edit { it[BASS_BOOST] = strength }
    }

    suspend fun updateLoudness(enabled: Boolean) {
        dataStore.edit { it[LOUDNESS_ENABLED] = enabled }
    }

    suspend fun updatePlaybackSpeed(speed: Float) {
        dataStore.edit { it[PLAYBACK_SPEED] = speed }
    }

    suspend fun updatePlaybackPitch(pitch: Float) {
        dataStore.edit { it[PLAYBACK_PITCH] = pitch }
    }

    suspend fun updateSkipSilence(enabled: Boolean) {
        dataStore.edit { it[SKIP_SILENCE] = enabled }
    }
}
