package com.synth.synthmusic.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.synth.synthmusic.data.local.database.WaveformDataDao
import com.synth.synthmusic.data.media.MediaPlaybackManager
import com.synth.synthmusic.data.media.waveform.WaveformGenerator
import com.synth.synthmusic.domain.repository.SettingsRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.CheckWritePermissionUseCase
import com.synth.synthmusic.domain.usecase.WriteArtworkToMp3UseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the now playing screen managing playback controls and state.
 */
@OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    kotlinx.coroutines.FlowPreview::class
)
class NowPlayingViewModel(
    private val playbackManager: MediaPlaybackManager,
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val waveformGenerator: WaveformGenerator,
    private val waveformDataDao: WaveformDataDao,
    private val writeArtworkUseCase: WriteArtworkToMp3UseCase,
    private val checkWritePermission: CheckWritePermissionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    private val _hasWritePermission = MutableStateFlow(checkWritePermission())
    val hasWritePermission: StateFlow<Boolean> = _hasWritePermission.asStateFlow()

    init {
        playbackManager.playbackState
            .map { it.currentSongId }
            .filterNotNull()
            .debounce(300)
            .flatMapLatest { songId ->
                combine(
                    playbackManager.playbackState,
                    songRepository.observeSongById(songId),
                    settingsRepository.settings
                ) { playback, song, settings ->
                    Triple(playback, song, settings)
                }
            }
            .onEach { (playback, song, settings) ->
                _uiState.update {
                    it.copy(
                        song = song,
                        isPlaying = playback.isPlaying,
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        repeatMode = playback.repeatMode,
                        shuffleEnabled = playback.shuffleEnabled,
                        rating = song?.rating ?: 0f,
                        isFavorite = song?.isFavorite ?: false,
                        playbackSpeed = settings.playbackSpeed,
                        playbackPitch = settings.playbackPitch
                    )
                }
            }
            .launchIn(viewModelScope)

        // Load waveform when song changes; cancel previous generation on rapid skips
        playbackManager.playbackState
            .map { it.currentSongId }
            .filterNotNull()
            .flatMapLatest { songId ->
                flow { emit(loadWaveform(songId)) }
            }
            .onEach { amplitudes ->
                _uiState.update { it.copy(waveformAmplitudes = amplitudes) }
            }
            .launchIn(viewModelScope)

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
            is NowPlayingEvent.ToggleShuffle -> {
                playbackManager.setShuffleEnabled(!_uiState.value.shuffleEnabled)
            }
            is NowPlayingEvent.CycleRepeat -> playbackManager.cycleRepeatMode()
            is NowPlayingEvent.UpdateRating -> {
                val songId = _uiState.value.song?.id ?: return
                viewModelScope.launch {
                    songRepository.updateSongRating(songId, event.rating)
                }
                _uiState.update { it.copy(rating = event.rating) }
            }
            NowPlayingEvent.ToggleFavorite -> {
                val songId = _uiState.value.song?.id ?: return
                val newValue = !_uiState.value.isFavorite
                viewModelScope.launch {
                    songRepository.updateSongFavorite(songId, newValue)
                }
                _uiState.update { it.copy(isFavorite = newValue) }
            }
            is NowPlayingEvent.SaveLyrics -> {
                val songId = _uiState.value.song?.id ?: return
                viewModelScope.launch {
                    songRepository.updateSongLyrics(songId, event.lyrics.takeIf { it.isNotBlank() })
                }
                _uiState.update { it.copy(song = it.song?.copy(lyrics = event.lyrics)) }
            }
            is NowPlayingEvent.SetPlaybackSpeed -> {
                viewModelScope.launch {
                    settingsRepository.updatePlaybackSpeed(event.speed)
                }
                _uiState.update { it.copy(playbackSpeed = event.speed) }
            }
            is NowPlayingEvent.SetPlaybackPitch -> {
                viewModelScope.launch {
                    settingsRepository.updatePlaybackPitch(event.pitch)
                }
                _uiState.update { it.copy(playbackPitch = event.pitch) }
            }
        }
    }

    fun refreshPermission() {
        _hasWritePermission.value = checkWritePermission()
    }

    fun updateArtwork(bytes: ByteArray?) {
        viewModelScope.launch {
            val song = _uiState.value.song ?: return@launch
            if (bytes != null) {
                writeArtworkUseCase(song, bytes)
            }
        }
    }

    fun removeArtwork() {
        viewModelScope.launch {
            val song = _uiState.value.song ?: return@launch
            writeArtworkUseCase.removeArtwork(song)
        }
    }

    private suspend fun loadWaveform(songId: String): List<Float> {
        return try {
            val cached = waveformDataDao.getBySongId(songId)
            if (cached != null) {
                cached.amplitudes
            } else {
                val song = songRepository.getSongById(songId) ?: return emptyList()
                val amplitudes = waveformGenerator.generate(song.uri, bars = 200)
                if (amplitudes.isNotEmpty()) {
                    waveformDataDao.insert(
                        com.synth.synthmusic.data.local.database.WaveformDataEntity(
                            songId = songId,
                            amplitudes = amplitudes.toList()
                        )
                    )
                }
                amplitudes.toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT release player here; it is managed by the service lifecycle
    }
}
