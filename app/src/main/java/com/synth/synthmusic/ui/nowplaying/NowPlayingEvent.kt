package com.synth.synthmusic.ui.nowplaying

/**
 * Events emitted by the now playing UI to the ViewModel.
 */
sealed class NowPlayingEvent {
    data object PlayPause : NowPlayingEvent()
    data object Next : NowPlayingEvent()
    data object Previous : NowPlayingEvent()
    data class Seek(val positionMs: Long) : NowPlayingEvent()
    data object ToggleShuffle : NowPlayingEvent()
    data object CycleRepeat : NowPlayingEvent()
    data class UpdateRating(val rating: Float) : NowPlayingEvent()
    data object ToggleFavorite : NowPlayingEvent()
}
