package com.synth.synthmusic.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

/**
 * ViewModel for the folder browser screen.
 */
class FoldersViewModel(
    songRepository: SongRepository
) : ViewModel() {

    data class FolderItem(
        val path: String,
        val name: String,
        val songCount: Int
    )

    val folders: StateFlow<List<FolderItem>> = songRepository.observeAllSongs()
        .map { songs ->
            songs.groupBy { File(it.path).parentFile?.absolutePath ?: "" }
                .map { (path, list) ->
                    FolderItem(
                        path = path,
                        name = File(path).name.ifBlank { path },
                        songCount = list.size
                    )
                }
                .sortedBy { it.name }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
