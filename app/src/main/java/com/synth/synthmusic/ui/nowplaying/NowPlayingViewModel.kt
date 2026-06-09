package com.synth.synthmusic.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.local.database.WaveformDataDao
import com.synth.synthmusic.data.media.PlaybackRepository
import com.synth.synthmusic.data.media.waveform.WaveformGenerator
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SettingsRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.CheckRecordAudioPermissionUseCase
import com.synth.synthmusic.domain.usecase.CheckWritePermissionUseCase
import com.synth.synthmusic.domain.usecase.WriteArtworkToMp3UseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the now playing screen managing playback controls and state.
 *
 * The ExoPlayer instance is **not** released here; its lifecycle is tied to [com.synth.synthmusic.service.PlaybackService].
 */
@OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    kotlinx.coroutines.FlowPreview::class
)
class NowPlayingViewModel(
    private val playbackRepository: PlaybackRepository,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    private val waveformGenerator: WaveformGenerator,
    private val waveformDataDao: WaveformDataDao,
    private val writeArtworkUseCase: WriteArtworkToMp3UseCase,
    private val checkWritePermission: CheckWritePermissionUseCase,
    private val checkRecordAudioPermission: CheckRecordAudioPermissionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    private val _hasWritePermission = MutableStateFlow(checkWritePermission())
    val hasWritePermission: StateFlow<Boolean> = _hasWritePermission.asStateFlow()

    init {
        _uiState.update { it.copy(hasRecordAudioPermission = checkRecordAudioPermission()) }

        // Pre-load the current track immediately so the screen never opens blank.
        val currentPlayback = playbackRepository.playbackState.value
        val currentSongId = currentPlayback.currentSongId
        if (currentSongId != null) {
            viewModelScope.launch {
                val song = songRepository.getSongById(currentSongId)
                if (song != null) {
                    _uiState.update {
                        it.copy(
                            song = song,
                            isPlaying = currentPlayback.isPlaying,
                            positionMs = playbackRepository.currentPositionMs.value,
                            durationMs = playbackRepository.currentDurationMs.value,
                            repeatMode = currentPlayback.repeatMode,
                            shuffleEnabled = currentPlayback.shuffleEnabled,
                            rating = song.rating,
                            isFavorite = song.isFavorite,
                            playbackSpeed = settingsRepository.settings.first().playbackSpeed,
                            playbackPitch = settingsRepository.settings.first().playbackPitch,
                            audioSessionId = playbackRepository.audioSessionId.value,
                            hasRecordAudioPermission = checkRecordAudioPermission(),
                            audioQualityLabel = buildAudioQualityLabel(song)
                        )
                    }
                }
            }
        }

        // Reactive stream: re-fetch song metadata only when currentSongId changes,
        // but continuously observe position/duration as separate high-frequency flows.
        playbackRepository.playbackState
            .map { it.currentSongId }
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { songId ->
                combine(
                    playbackRepository.playbackState,
                    songRepository.observeSongById(songId),
                    settingsRepository.settings,
                    playbackRepository.currentPositionMs,
                    playbackRepository.currentDurationMs
                ) { playback, song, settings, position, duration ->
                    Quintuple(playback, song, settings, position, duration)
                }
            }
            .onEach { (playback, song, settings, position, duration) ->
                _uiState.update {
                    it.copy(
                        song = song,
                        isPlaying = playback.isPlaying,
                        positionMs = position,
                        durationMs = duration,
                        repeatMode = playback.repeatMode,
                        shuffleEnabled = playback.shuffleEnabled,
                        rating = song?.rating ?: 0f,
                        isFavorite = song?.isFavorite ?: false,
                        playbackSpeed = settings.playbackSpeed,
                        playbackPitch = settings.playbackPitch,
                        audioSessionId = playbackRepository.audioSessionId.value,
                        hasRecordAudioPermission = checkRecordAudioPermission(),
                        audioQualityLabel = buildAudioQualityLabel(song)
                    )
                }
            }
            .launchIn(viewModelScope)

        // Load waveform when song changes; cancel previous generation on rapid skips
        playbackRepository.playbackState
            .map { it.currentSongId }
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { songId ->
                flow { emit(loadWaveform(songId)) }
            }
            .onEach { amplitudes ->
                _uiState.update { it.copy(waveformAmplitudes = amplitudes) }
            }
            .launchIn(viewModelScope)

        // Observe playback queue
        playbackRepository.currentQueue
            .onEach { list -> _uiState.update { it.copy(queueSongs = list) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: NowPlayingEvent) {
        when (event) {
            is NowPlayingEvent.ToggleShuffle -> {
                playbackRepository.setShuffleEnabled(!_uiState.value.shuffleEnabled)
            }
            is NowPlayingEvent.CycleRepeat -> playbackRepository.cycleRepeatMode()
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
                    val favoritesId = playlistRepository.getFavoritesPlaylistId()
                    if (favoritesId != null) {
                        if (newValue) {
                            playlistRepository.addSongToPlaylist(favoritesId, songId)
                        } else {
                            playlistRepository.removeSongFromPlaylist(favoritesId, songId)
                        }
                    }
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

    fun refreshRecordAudioPermission() {
        _uiState.update { it.copy(hasRecordAudioPermission = checkRecordAudioPermission()) }
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

    fun playQueueItem(index: Int) {
        playbackRepository.playQueueItem(index)
    }

    fun removeFromQueue(index: Int) {
        playbackRepository.removeFromQueue(index)
    }

    fun clearQueue() {
        playbackRepository.clearQueue()
    }

    private fun buildAudioQualityLabel(song: com.synth.synthmusic.domain.model.Song?): String {
        if (song == null) return ""
        val sampleRateKhz = song.sampleRate / 1000f
        val sampleRateText = if (sampleRateKhz == sampleRateKhz.toInt().toFloat()) {
            "${sampleRateKhz.toInt()} kHz"
        } else {
            String.format(java.util.Locale.US, "%.1f kHz", sampleRateKhz)
        }
        val format = song.path.substringAfterLast('.', "Unknown").uppercase()
        return "$sampleRateText • ${song.bitrate} kbps • $format"
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

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
