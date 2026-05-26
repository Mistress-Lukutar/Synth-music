package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.datastore.SettingsDataStore
import com.synth.synthmusic.domain.model.AccentColor
import com.synth.synthmusic.domain.model.AppSettings
import com.synth.synthmusic.domain.model.ReplayGainMode
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
    override suspend fun updateCrossfade(durationMs: Int) = dataStore.updateCrossfade(durationMs)
    override suspend fun updateGapless(enabled: Boolean) = dataStore.updateGapless(enabled)
    override suspend fun updateReplayGain(mode: ReplayGainMode) = dataStore.updateReplayGain(mode)
    override suspend fun updateAutoRescan(enabled: Boolean) = dataStore.updateAutoRescan(enabled)
    override suspend fun updateEqEnabled(enabled: Boolean) = dataStore.updateEqEnabled(enabled)
    override suspend fun updateEqPresetId(id: Long?) = dataStore.updateEqPresetId(id)
    override suspend fun updateBassBoost(strength: Int) = dataStore.updateBassBoost(strength)
    override suspend fun updateLoudness(enabled: Boolean) = dataStore.updateLoudness(enabled)
}
