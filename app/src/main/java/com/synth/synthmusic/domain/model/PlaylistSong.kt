package com.synth.synthmusic.domain.model

/**
 * Junction entity linking playlists to songs with ordering.
 *
 * @param playlistId Reference to the playlist.
 * @param songId Reference to the song.
 * @param position Order index within the playlist.
 */
data class PlaylistSong(
    val playlistId: Long,
    val songId: String,
    val position: Int
)
