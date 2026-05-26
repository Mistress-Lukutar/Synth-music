package com.synth.synthmusic.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the application.
 */
@Serializable
object LibraryRoute

@Serializable
object NowPlayingRoute

@Serializable
object QueueRoute

@Serializable
object SearchRoute

@Serializable
object EqualizerRoute

@Serializable
data class SongInfoRoute(val songId: String)

@Serializable
data class EditMetadataRoute(val songId: String)

@Serializable
data class BatchEditRoute(val songIds: List<String>)

@Serializable
data class PlaylistDetailRoute(val playlistId: Long)

@Serializable
data class FolderDetailRoute(val folderPath: String)

@Serializable
data class AlbumDetailRoute(val albumTitle: String, val albumArtist: String)

@Serializable
object BookmarksRoute

@Serializable
object VisualizerRoute

@Serializable
object SettingsRoute

@Serializable
object DownloadsRoute
