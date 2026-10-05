package com.synth.synthmusic.domain.usecase

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import com.synth.synthmusic.data.local.cover.CoverCache
import com.synth.synthmusic.data.local.database.WaveformDataDao
import com.synth.synthmusic.data.media.waveform.WaveformPreloader
import com.synth.synthmusic.domain.model.Album
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.AlbumRepository
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Use case for scanning device storage and indexing MP3 files into the local database.
 *
 * The scan is incremental: the cheap MediaStore query always runs, but expensive
 * per-file work (metadata extraction via [MediaMetadataRetriever], artwork decoding
 * and caching) is only performed for files that are new or whose size / modification
 * date changed since the previous scan. Unchanged files reuse their stored database
 * row, which makes a rescan of an unmodified library take a fraction of a second.
 * ReplayGain tags are not read here; they are extracted lazily on first playback
 * (see [com.synth.synthmusic.data.media.ReplayGainReader]).
 */
class ScanMusicUseCase(
    private val context: Context,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val playlistRepository: PlaylistRepository,
    private val waveformPreloader: WaveformPreloader,
    private val waveformDataDao: WaveformDataDao,
    private val coverCache: CoverCache
) {

    /** Parallelism for the heavy per-file extraction. Bounded to avoid saturating flash I/O. */
    private val extractionDispatcher = Dispatchers.IO.limitedParallelism(4)

    suspend operator fun invoke(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val songs = scanSongs()
            songRepository.upsertSongs(songs)

            val existingSongs = songRepository.getAllSongs()
            val scannedIds = songs.map { it.id }.toSet()
            val toDelete = existingSongs.map { it.id }.filter { it !in scannedIds }
            toDelete.forEach { songRepository.deleteSong(it) }

            waveformPreloader.preload(songs)
            waveformDataDao.deleteOrphaned()

            val albums = deriveAlbums(songs)
            albumRepository.replaceAllAlbums(albums)

            val artists = deriveArtists(songs, albums)
            artistRepository.replaceAllArtists(artists)

            playlistRepository.ensureFavoritesPlaylist()
            playlistRepository.ensureHistoryPlaylist()
            playlistRepository.ensureTopTracksPlaylist()

            songs.size
        }
    }

    /**
     * Queries MediaStore for all music files, then resolves each entry either by reusing
     * the stored database row (unchanged files) or by running full metadata extraction
     * (new and modified files, processed in parallel).
     */
    private suspend fun scanSongs(): List<Song> {
        val existing = songRepository.getAllSongs()
        val existingById = existing.associateBy { it.id }
        val existingByPath = existing.associateBy { it.path }
        val entries = queryMediaStoreEntries()

        return coroutineScope {
            entries.map { entry ->
                // MediaStore can reassign _ID when a file is rewritten or moved
                // outside the app; reattach by path so playlist membership and
                // user data (rating, play count, lyrics) survive the rescan.
                val cached = existingById[entry.id] ?: existingByPath[entry.path]
                if (cached != null && isUpToDate(cached, entry)) {
                    async(Dispatchers.Default) {
                        if (cached.uri == entry.uri) cached else cached.copy(uri = entry.uri)
                    }
                } else {
                    async(extractionDispatcher) { extractSong(entry, cached) }
                }
            }.map { it.await() }
        }
    }

    /**
     * Returns true when the stored [song] still matches the file described by [entry]
     * and its cached artwork file is still present on disk.
     */
    private fun isUpToDate(song: Song, entry: MediaStoreEntry): Boolean {
        if (song.dateModified != entry.dateModifiedMs || song.fileSize != entry.fileSize) {
            return false
        }
        // Song artwork lives in the app cache dir and may have been evicted by the system;
        // re-extract it when the referenced file is gone.
        val artworkUri = song.artworkUri ?: return true
        val artworkPath = Uri.parse(artworkUri).path ?: return false
        return File(artworkPath).exists()
    }

    /**
     * Lightweight row of the MediaStore query — only fields readable from the cursor.
     */
    private data class MediaStoreEntry(
        val id: String,
        val mediaStoreId: Long,
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String,
        val durationMs: Long,
        val trackNumber: Int,
        val year: Int,
        val path: String,
        val uri: String,
        val dateAddedMs: Long,
        val dateModifiedMs: Long,
        val fileSize: Long
    )

    @SuppressLint("InlinedApi")
    private fun queryMediaStoreEntries(): List<MediaStoreEntry> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val entries = mutableListOf<MediaStoreEntry>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumArtistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val mediaStoreId = cursor.getLong(idCol)
                val path = cursor.getString(dataCol) ?: continue
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                entries.add(
                    MediaStoreEntry(
                        id = mediaStoreId.toString(),
                        mediaStoreId = mediaStoreId,
                        title = cursor.getString(titleCol) ?: "Unknown Title",
                        artist = artist,
                        album = cursor.getString(albumCol) ?: "Unknown Album",
                        albumArtist = cursor.getString(albumArtistCol) ?: artist,
                        durationMs = cursor.getLong(durationCol),
                        trackNumber = cursor.getInt(trackCol),
                        year = cursor.getInt(yearCol),
                        path = path,
                        uri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            mediaStoreId.toString()
                        ).toString(),
                        dateAddedMs = cursor.getLong(addedCol) * 1000,
                        dateModifiedMs = cursor.getLong(modifiedCol) * 1000,
                        fileSize = cursor.getLong(sizeCol)
                    )
                )
            }
        }
        return entries
    }

    /**
     * Runs the expensive per-file work for a new or modified [entry]:
     * metadata extraction via [MediaMetadataRetriever] and artwork caching.
     * [cached] is the previous database row for the same file, if any.
     */
    private fun extractSong(entry: MediaStoreEntry, cached: Song?): Song {
        // Keep the cached database id when MediaStore reassigned _ID for the same
        // file: the id is referenced by playlists, bookmarks and playback state.
        val id = cached?.id ?: entry.id
        var artworkUri = resolveArtwork(id)
        var bitrate = 0
        var sampleRate = 0
        var genre = ""

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, entry.uri.toUri())
            bitrate = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_BITRATE
            )?.toIntOrNull()?.div(1000) ?: 0
            sampleRate = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_SAMPLERATE
            )?.toIntOrNull() ?: 0
            genre = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_GENRE
            ) ?: ""
            if (artworkUri == null) {
                artworkUri = retriever.embeddedPicture?.let { bytes ->
                    Uri.fromFile(coverCache.saveSongArtwork(id, bytes)).toString()
                }
            }
        } catch (_: Exception) {
            // ignore corrupted files
        } finally {
            retriever.release()
        }

        if (artworkUri == null) {
            artworkUri = extractMediaStoreThumbnail(id, entry.mediaStoreId) ?: cached?.artworkUri
        }

        return Song(
            id = id,
            title = entry.title,
            artist = entry.artist,
            album = entry.album,
            albumArtist = entry.albumArtist,
            durationMs = entry.durationMs,
            trackNumber = entry.trackNumber,
            year = entry.year,
            genre = genre,
            comment = cached?.comment ?: "",
            path = entry.path,
            uri = entry.uri,
            bitrate = bitrate,
            sampleRate = sampleRate,
            fileSize = entry.fileSize,
            artworkUri = artworkUri,
            rating = 0f,
            playCount = 0,
            lastPlayed = null,
            dateAdded = entry.dateAddedMs,
            dateModified = entry.dateModifiedMs,
            lyrics = null,
            replayGainTrackDb = cached?.replayGainTrackDb,
            replayGainAlbumDb = cached?.replayGainAlbumDb
        )
    }

    /**
     * Returns the URI of an already-cached artwork for the song with [id],
     * or null when no cached cover file exists.
     */
    private fun resolveArtwork(id: String): String? {
        val file = coverCache.getCoverFile(CoverCache.Type.SONG, id) ?: return null
        return Uri.fromFile(file).toString()
    }

    /**
     * Reads the MediaStore album art thumbnail for the song with [mediaStoreId]
     * and stores it in [CoverCache] under [id]. Returns the cached file URI,
     * or null when MediaStore has no thumbnail.
     */
    private fun extractMediaStoreThumbnail(id: String, mediaStoreId: Long): String? {
        return try {
            val mediaStoreUri =
                "content://media/external/audio/media/$mediaStoreId/albumart".toUri()
            context.contentResolver.openInputStream(mediaStoreUri)?.use { input ->
                val bytes = input.readBytes()
                val file = coverCache.saveSongArtwork(id, bytes)
                Uri.fromFile(file).toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveAlbums(songs: List<Song>): List<Album> {
        return songs.groupBy { it.album to it.albumArtist }
            .map { (key, tracks) ->
                val (albumTitle, albumArtist) = key
                Album(
                    id = "$albumTitle|$albumArtist".hashCode().toString(),
                    title = albumTitle,
                    artist = albumArtist,
                    year = tracks.maxOfOrNull { it.year } ?: 0,
                    artworkUri = tracks.firstNotNullOfOrNull { it.artworkUri },
                    songCount = tracks.size,
                    totalDurationMs = tracks.sumOf { it.durationMs }
                )
            }
    }

    private fun deriveArtists(songs: List<Song>, albums: List<Album>): List<Artist> {
        return songs.groupBy { it.artist }
            .map { (name, tracks) ->
                Artist(
                    id = name.hashCode().toString(),
                    name = name,
                    songCount = tracks.size,
                    albumCount = albums.count { it.artist == name },
                    artworkUri = tracks.firstNotNullOfOrNull { it.artworkUri }
                )
            }
    }
}
