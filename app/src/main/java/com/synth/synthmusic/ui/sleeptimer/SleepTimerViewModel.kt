package com.synth.synthmusic.ui.sleeptimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.MediaPlaybackManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for sleep timer functionality.
 */
class SleepTimerViewModel(
    private val playbackManager: MediaPlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepTimerUiState())
    val uiState: StateFlow<SleepTimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun startTimer(minutes: Int) {
        stopTimer()
        val totalMs = minutes * 60 * 1000L
        _uiState.update { it.copy(isActive = true, remainingMs = totalMs, totalMs = totalMs) }
        timerJob = viewModelScope.launch {
            val endTime = System.currentTimeMillis() + totalMs
            while (isActive && System.currentTimeMillis() < endTime) {
                val remaining = endTime - System.currentTimeMillis()
                _uiState.update { it.copy(remainingMs = remaining.coerceAtLeast(0)) }
                delay(1000)
            }
            if (isActive) {
                playbackManager.playPause()
                _uiState.update { SleepTimerUiState() }
            }
        }
    }

    fun startEndOfTrackTimer() {
        stopTimer()
        _uiState.update { it.copy(isActive = true, endOfTrack = true) }
        // Simplified: stop when current track finishes
        timerJob = viewModelScope.launch {
            val stateFlow = playbackManager.playbackState
            var wasPlaying = stateFlow.value.isPlaying
            while (isActive) {
                val state = stateFlow.value
                if (wasPlaying && !state.isPlaying && state.positionMs < 1000) {
                    playbackManager.playPause()
                    _uiState.update { SleepTimerUiState() }
                    break
                }
                wasPlaying = state.isPlaying
                delay(1000)
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { SleepTimerUiState() }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

/**
 * UI state for the sleep timer.
 */
data class SleepTimerUiState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0,
    val totalMs: Long = 0,
    val endOfTrack: Boolean = false
)
