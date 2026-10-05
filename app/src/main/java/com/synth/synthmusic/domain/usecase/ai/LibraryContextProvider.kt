package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.data.media.PlaybackRepository
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.first

/**
 * Builds the small app-context block injected after the assistant system
 * prompt so the model knows basic library facts and what is playing.
 * Capped to roughly [MAX_TOKENS] tokens.
 */
class LibraryContextProvider(
    private val songRepository: SongRepository,
    private val playbackRepository: PlaybackRepository
) {

    /**
     * Returns a compact context block, or null when the library is empty.
     */
    suspend fun contextBlock(): String? {
        val songs = songRepository.getAllSongs()
        if (songs.isEmpty()) return null
        val playback = playbackRepository.playbackState.value
        val currentSong = playback.currentSongId
            ?.let { id -> songs.firstOrNull { it.id == id } }
        val lines = buildList {
            add(
                "Library context: ${songs.size} songs, " +
                    "${songs.map { it.album }.distinct().size} albums, " +
                    "${songs.map { it.artist }.distinct().size} artists."
            )
            currentSong?.let {
                add(
                    "Now playing: \"${it.title}\" by ${it.artist} " +
                        if (playback.isPlaying) "(playing)" else "(paused)"
                )
            }
        }
        return lines.joinToString("\n").take(MAX_TOKENS * CHARS_PER_TOKEN)
    }

    private companion object {
        const val MAX_TOKENS = 150
        const val CHARS_PER_TOKEN = 4
    }
}
