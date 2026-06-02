package com.synth.synthmusic.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the application.
 */
@Serializable
object SplashRoute

@Serializable
object HomeRoute

@Serializable
object NowPlayingRoute

@Serializable
object SearchRoute

@Serializable
data class SongInfoRoute(val songId: String)

@Serializable
data class EditMetadataRoute(val songId: String)

@Serializable
data class PlaylistDetailRoute(val playlistId: Long)

@Serializable
data class AlbumDetailRoute(val albumTitle: String, val albumArtist: String)

@Serializable
data class ArtistDetailRoute(val artistName: String)

@Serializable
object VisualizerRoute

@Serializable
object SettingsRoute


