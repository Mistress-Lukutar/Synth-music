package com.synth.synthmusic.ui.nowplaying

/**
 * Events emitted by the now playing UI to the ViewModel.
 */
sealed class NowPlayingEvent {
    data object ToggleShuffle : NowPlayingEvent()
    data object CycleRepeat : NowPlayingEvent()
    data class UpdateRating(val rating: Float) : NowPlayingEvent()
    data object ToggleFavorite : NowPlayingEvent()
    data class SaveLyrics(val lyrics: String) : NowPlayingEvent()
    data class SetPlaybackSpeed(val speed: Float) : NowPlayingEvent()
    data class SetPlaybackPitch(val pitch: Float) : NowPlayingEvent()
}
