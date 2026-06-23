package com.synth.synthmusic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.model.AccentColor
import com.synth.synthmusic.domain.model.AppSettings
import com.synth.synthmusic.domain.model.ThemeMode
import com.synth.synthmusic.domain.repository.SettingsRepository
import com.synth.synthmusic.domain.usecase.ScanMusicUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val scanMusicUseCase: ScanMusicUseCase
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    fun updateTheme(theme: ThemeMode) {
        viewModelScope.launch { settingsRepository.updateTheme(theme) }
    }

    fun updateAccentColor(color: AccentColor) {
        viewModelScope.launch { settingsRepository.updateAccentColor(color) }
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

    fun rescanLibrary() {
        viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null
            scanMusicUseCase()
                .onSuccess {
                    _isScanning.value = false
                }
                .onFailure { error ->
                    _isScanning.value = false
                    _scanError.value = error.message
                }
        }
    }

    fun consumeScanError() {
        _scanError.value = null
    }
}
