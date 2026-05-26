package com.synth.synthmusic.ui.metadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for song info screen.
 */
class SongInfoViewModel(
    songId: String,
    songRepository: SongRepository
) : ViewModel() {

    val song: StateFlow<Song?> = songRepository.observeSongById(songId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
