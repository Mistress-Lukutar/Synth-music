package com.synth.synthmusic.ui.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.AudioEffectsManager
import com.synth.synthmusic.data.media.MediaPlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the equalizer screen.
 */
class EqualizerViewModel(
    private val audioEffectsManager: AudioEffectsManager,
    playbackManager: MediaPlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    init {
        playbackManager.initAudioEffects(audioEffectsManager)

        val numBands = audioEffectsManager.getNumberOfBands()
        val frequencies = (0 until numBands).map { audioEffectsManager.getCenterFreq(it.toShort()) }
        val levels = (0 until numBands).map { audioEffectsManager.getBandLevel(it.toShort()).toInt() }
        val (min, max) = audioEffectsManager.getBandLevelRange()

        _uiState.update {
            it.copy(
                isEnabled = audioEffectsManager.isEqEnabled,
                frequencies = frequencies,
                bandLevels = levels,
                minLevel = min.toInt(),
                maxLevel = max.toInt(),
                bassBoost = audioEffectsManager.getBassBoost().toInt()
            )
        }

        audioEffectsManager.presets
            .onEach { list -> _uiState.update { it.copy(presets = list) } }
            .launchIn(viewModelScope)
    }

    fun setEnabled(enabled: Boolean) {
        audioEffectsManager.setEnabled(enabled)
        _uiState.update { it.copy(isEnabled = enabled) }
    }

    fun setBandLevel(index: Int, level: Int) {
        audioEffectsManager.setBandLevel(index.toShort(), level.toShort())
        val newLevels = _uiState.value.bandLevels.toMutableList()
        newLevels[index] = level
        _uiState.update { it.copy(bandLevels = newLevels) }
    }

    fun setBassBoost(strength: Int) {
        audioEffectsManager.setBassBoost(strength.toShort())
        _uiState.update { it.copy(bassBoost = strength) }
    }

    fun setLoudness(enabled: Boolean) {
        audioEffectsManager.setLoudnessEnabled(enabled)
        _uiState.update { it.copy(loudnessEnabled = enabled) }
    }

    fun savePreset(name: String) {
        viewModelScope.launch {
            audioEffectsManager.savePreset(name, _uiState.value.bandLevels)
        }
    }

    fun loadPreset(presetId: Long) {
        viewModelScope.launch {
            audioEffectsManager.loadPreset(presetId)
            val newLevels = (0 until _uiState.value.bandLevels.size).map {
                audioEffectsManager.getBandLevel(it.toShort()).toInt()
            }
            _uiState.update { it.copy(bandLevels = newLevels) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Effects are released when playback manager is released
    }
}

/**
 * UI state for the equalizer screen.
 */
data class EqualizerUiState(
    val isEnabled: Boolean = false,
    val frequencies: List<Int> = emptyList(),
    val bandLevels: List<Int> = emptyList(),
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val bassBoost: Int = 0,
    val loudnessEnabled: Boolean = false,
    val presets: List<Pair<Long, String>> = emptyList()
)
