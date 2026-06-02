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
        private val FADE_DURATION = intPreferencesKey("fade_duration_ms")
        private val AUTO_RESCAN = booleanPreferencesKey("auto_rescan")
        private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        private val PLAYBACK_PITCH = floatPreferencesKey("playback_pitch")
        private val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[THEME]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.DARK,
            accentColor = prefs[ACCENT_COLOR]?.let { AccentColor.valueOf(it) } ?: AccentColor.YELLOW,
            fadeDurationMs = prefs[FADE_DURATION] ?: 300,
            autoRescan = prefs[AUTO_RESCAN] ?: true,
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

    suspend fun updateFadeDuration(durationMs: Int) {
        dataStore.edit { it[FADE_DURATION] = durationMs }
    }

    suspend fun updateAutoRescan(enabled: Boolean) {
        dataStore.edit { it[AUTO_RESCAN] = enabled }
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
