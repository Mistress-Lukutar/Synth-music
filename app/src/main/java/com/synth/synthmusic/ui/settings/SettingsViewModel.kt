package com.synth.synthmusic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.model.AccentColor
import com.synth.synthmusic.domain.model.AppSettings
import com.synth.synthmusic.domain.model.ThemeMode
import com.synth.synthmusic.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun updateTheme(theme: ThemeMode) {
        viewModelScope.launch { settingsRepository.updateTheme(theme) }
    }

    fun updateAccentColor(color: AccentColor) {
        viewModelScope.launch { settingsRepository.updateAccentColor(color) }
    }

    fun updateFadeDuration(durationMs: Int) {
        viewModelScope.launch { settingsRepository.updateFadeDuration(durationMs) }
    }

    fun updateAutoRescan(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoRescan(enabled) }
    }

    fun updatePlaybackSpeed(speed: Float) {
        viewModelScope.launch { settingsRepository.updatePlaybackSpeed(speed) }
    }

    fun updatePlaybackPitch(pitch: Float) {
        viewModelScope.launch { settingsRepository.updatePlaybackPitch(pitch) }
    }

    fun updateSkipSilence(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateSkipSilence(enabled) }
    }
}
