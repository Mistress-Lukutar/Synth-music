package com.synth.synthmusic.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.synth.synthmusic.data.media.MediaPlaybackManager
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the now playing screen managing playback controls and state.
 */
class NowPlayingViewModel(
    private val playbackManager: MediaPlaybackManager,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    init {
        combine(
            playbackManager.playbackState,
            songRepository.observeSongById(
                playbackManager.playbackState.value.currentSongId ?: ""
            )
        ) { playback, song ->
            playback to song
        }.onEach { (playback, song) ->
            _uiState.update {
                it.copy(
                    song = song,
                    isPlaying = playback.isPlaying,
                    positionMs = playback.positionMs,
                    durationMs = playback.durationMs,
                    repeatMode = playback.repeatMode,
                    shuffleEnabled = playback.shuffleEnabled
                )
            }
        }.launchIn(viewModelScope)

        // Poll position while playing
        viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.isPlaying) {
                    _uiState.update {
                        it.copy(positionMs = playbackManager.player.currentPosition)
                    }
                }
                delay(1000)
            }
        }
    }

    fun onEvent(event: NowPlayingEvent) {
        when (event) {
            is NowPlayingEvent.PlayPause -> playbackManager.playPause()
            is NowPlayingEvent.Next -> playbackManager.next()
            is NowPlayingEvent.Previous -> playbackManager.previous()
            is NowPlayingEvent.Seek -> playbackManager.seekTo(event.positionMs)
            is NowPlayingEvent.ToggleShuffle -> {
                playbackManager.setShuffleEnabled(!_uiState.value.shuffleEnabled)
            }
            is NowPlayingEvent.CycleRepeat -> playbackManager.cycleRepeatMode()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT release player here; it is managed by the service lifecycle
    }
}
