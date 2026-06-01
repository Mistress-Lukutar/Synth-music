package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.AccentColor
import com.synth.synthmusic.domain.model.AppSettings
import com.synth.synthmusic.domain.model.ReplayGainMode
import com.synth.synthmusic.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for application settings.
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateTheme(theme: ThemeMode)
    suspend fun updateAccentColor(color: AccentColor)
    suspend fun updateFadeDuration(durationMs: Int)
    suspend fun updateReplayGain(mode: ReplayGainMode)
    suspend fun updateAutoRescan(enabled: Boolean)
    suspend fun updateEqEnabled(enabled: Boolean)
    suspend fun updateEqPresetId(id: Long?)
    suspend fun updateBassBoost(strength: Int)
    suspend fun updateLoudness(enabled: Boolean)
    suspend fun updatePlaybackSpeed(speed: Float)
    suspend fun updatePlaybackPitch(pitch: Float)
    suspend fun updateSkipSilence(enabled: Boolean)
}
