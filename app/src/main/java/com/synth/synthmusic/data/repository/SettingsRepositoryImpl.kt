package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.datastore.SettingsDataStore
import com.synth.synthmusic.domain.model.AccentColor
import com.synth.synthmusic.domain.model.AppSettings
import com.synth.synthmusic.domain.model.ThemeMode
import com.synth.synthmusic.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of [SettingsRepository] backed by DataStore.
 */
class SettingsRepositoryImpl(
    private val dataStore: SettingsDataStore
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.settings

    override suspend fun updateTheme(theme: ThemeMode) = dataStore.updateTheme(theme)
    override suspend fun updateAccentColor(color: AccentColor) = dataStore.updateAccentColor(color)
    override suspend fun updateFadeDuration(durationMs: Int) = dataStore.updateFadeDuration(durationMs)
    override suspend fun updateAutoRescan(enabled: Boolean) = dataStore.updateAutoRescan(enabled)
    override suspend fun updatePlaybackSpeed(speed: Float) = dataStore.updatePlaybackSpeed(speed)
    override suspend fun updatePlaybackPitch(pitch: Float) = dataStore.updatePlaybackPitch(pitch)
    override suspend fun updateSkipSilence(enabled: Boolean) = dataStore.updateSkipSilence(enabled)
}
