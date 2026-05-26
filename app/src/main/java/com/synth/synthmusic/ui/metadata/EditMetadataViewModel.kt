package com.synth.synthmusic.ui.metadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

/**
 * ViewModel for editing song metadata in the audio file and database.
 */
class EditMetadataViewModel(
    private val songId: String,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _song = MutableStateFlow<Song?>(null)
    val song: StateFlow<Song?> = _song.asStateFlow()

    init {
        songRepository.observeSongById(songId)
            .onEach { _song.value = it }
            .launchIn(viewModelScope)
    }

    fun save(
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: String,
        comment: String,
        lyrics: String? = null,
        artworkUri: String? = null
    ) {
        val current = _song.value ?: return
        viewModelScope.launch {
            // Write to file using JAudioTagger
            try {
                val audioFile = AudioFileIO.read(java.io.File(current.path))
                val tag = audioFile.tagOrCreateAndSetDefault
                tag.setField(FieldKey.TITLE, title)
                tag.setField(FieldKey.ARTIST, artist)
                tag.setField(FieldKey.ALBUM, album)
                tag.setField(FieldKey.GENRE, genre)
                tag.setField(FieldKey.YEAR, year)
                tag.setField(FieldKey.COMMENT, comment)
                lyrics?.let { tag.setField(FieldKey.LYRICS, it) }
                AudioFileIO.write(audioFile)
            } catch (_: Exception) {
                // If file write fails, still update DB
            }

            // Update database
            songRepository.saveSongs(
                listOf(
                    current.copy(
                        title = title,
                        artist = artist,
                        album = album,
                        genre = genre,
                        year = year.toIntOrNull() ?: current.year,
                        comment = comment,
                        lyrics = lyrics ?: current.lyrics,
                        artworkUri = artworkUri ?: current.artworkUri
                    )
                )
            )
        }
    }
}
