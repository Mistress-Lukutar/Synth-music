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
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File

/** Matches metadata-write temp leftovers, both legacy and dot-prefixed names. */
private val TEMP_FILE_REGEX = Regex(".+\\.synthtmp\\..+")

/** Temps younger than this may belong to a write in progress and are never deleted. */
private const val TEMP_GRACE_PERIOD_MS = 10 * 60_000L

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

    /**
     * Scans device storage and indexes MP3 files into the local database.
     *
     * @param forceFullRefresh when true, the incremental cache is bypassed: every
     * file is re-read from disk (duration, tags, lyrics, artwork) instead of
     * trusting MediaStore's row or the stored artwork cache. Song ids are kept
     * stable, so playlists, bookmarks and playback state survive the refresh.
     */
    suspend operator fun invoke(forceFullRefresh: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val songs = scanSongs(forceFullRefresh)
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
    private suspend fun scanSongs(forceFullRefresh: Boolean): List<Song> {
        val existing = songRepository.getAllSongs()
        val existingById = existing.associateBy { it.id }
        val existingByPath = existing.associateBy { it.path }
        cleanupOrphanedTempFiles(existing.mapNotNull { File(it.path).parentFile }.toSet())
        val entries = queryMediaStoreEntries()
        val extractor: (MediaStoreEntry, Song?) -> Song =
            if (forceFullRefresh) this::extractSongFull else this::extractSong

        return coroutineScope {
            entries.map { entry ->
                // MediaStore can reassign _ID when a file is rewritten or moved
                // outside the app; reattach by path so playlist membership and
                // user data (rating, play count, lyrics) survive the rescan.
                val cached = existingById[entry.id] ?: existingByPath[entry.path]
                if (cached != null && !forceFullRefresh && isUpToDate(cached, entry)) {
                    async(Dispatchers.Default) {
                        if (cached.uri == entry.uri) cached else cached.copy(uri = entry.uri)
                    }
                } else {
                    async(extractionDispatcher) { extractor(entry, cached) }
                }
            }.map { it.await() }
        }
    }

    /**
     * Deletes metadata-write temp leftovers (`*.synthtmp.*`, including the older
     * non-hidden names) from the directories of indexed songs. A temp can survive
     * a write when the process is killed mid-write; deleting the file alone is not
     * enough because MediaStore keeps the row, so the stale MediaStore entry is
     * removed as well. Files written within [TEMP_GRACE_PERIOD_MS] are skipped —
     * they may belong to a metadata write currently in progress.
     */
    private fun cleanupOrphanedTempFiles(directories: Set<File>) {
        val now = System.currentTimeMillis()
        for (dir in directories) {
            val leftovers = dir.listFiles { file ->
                file.isFile &&
                    TEMP_FILE_REGEX.matches(file.name) &&
                    now - file.lastModified() > TEMP_GRACE_PERIOD_MS
            } ?: continue
            for (temp in leftovers) {
                if (temp.delete()) {
                    removeMediaStoreEntry(temp.absolutePath)
                }
            }
        }
    }

    /**
     * Removes the MediaStore row for [path] so the library scan prunes the
     * corresponding database row instead of resurrecting a deleted file.
     */
    private fun removeMediaStoreEntry(path: String) {
        runCatching {
            context.contentResolver.delete(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(path)
            )
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
                // MediaStore rows can outlive their file (deleted while the app was
                // dead, pending scanner cleanup); skip them so the scan prunes the
                // stale database row instead of keeping an unplayable entry.
                if (!File(path).exists()) continue
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
     * Re-extracts a song entirely from the file on disk, ignoring both the stored
     * database row and MediaStore's cached metadata. Used by the force refresh:
     * MediaStore itself can hold stale values (duration 0 from a scan of a
     * half-written file, outdated titles after external tag edits), so duration,
     * tags and artwork are read fresh. Falls back to MediaStore values for fields
     * that cannot be read from the file. User data (rating, play count, favorite)
     * is restored by the upsert merge; ReplayGain is preserved lazily.
     */
    private fun extractSongFull(entry: MediaStoreEntry, cached: Song?): Song {
        val id = cached?.id ?: entry.id
        var durationMs = entry.durationMs
        var bitrate = 0
        var sampleRate = 0
        var mediaStoreGenre = ""

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(entry.path)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0 } ?: entry.durationMs
            bitrate = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_BITRATE
            )?.toIntOrNull()?.div(1000) ?: 0
            sampleRate = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_SAMPLERATE
            )?.toIntOrNull() ?: 0
            mediaStoreGenre = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_GENRE
            ) ?: ""
        } catch (_: Exception) {
            // ignore unreadable files; MediaStore entry values are used below
        } finally {
            retriever.release()
        }

        val tag: Tag? = runCatching {
            AudioFileIO.read(File(entry.path)).tagOrCreateAndSetDefault
        }.getOrNull()

        // Artwork is re-derived from the file; a stale cached cover is dropped
        // when the file no longer carries embedded art.
        val artworkBytes = tag?.firstArtwork?.binaryData
        val artworkUri: String? = if (artworkBytes != null) {
            Uri.fromFile(coverCache.saveSongArtwork(id, artworkBytes)).toString()
        } else {
            coverCache.deleteCover(CoverCache.Type.SONG, id)
            extractMediaStoreThumbnail(id, entry.mediaStoreId)
        }

        return Song(
            id = id,
            title = firstField(tag, FieldKey.TITLE) ?: entry.title,
            artist = firstField(tag, FieldKey.ARTIST) ?: entry.artist,
            album = firstField(tag, FieldKey.ALBUM) ?: entry.album,
            albumArtist = firstField(tag, FieldKey.ALBUM_ARTIST)
                ?: firstField(tag, FieldKey.ARTIST) ?: entry.albumArtist,
            durationMs = durationMs,
            trackNumber = firstField(tag, FieldKey.TRACK)
                ?.substringBefore('/')?.trim()?.toIntOrNull() ?: entry.trackNumber,
            year = firstField(tag, FieldKey.YEAR)?.toIntOrNull() ?: entry.year,
            genre = firstField(tag, FieldKey.GENRE) ?: mediaStoreGenre,
            comment = firstField(tag, FieldKey.COMMENT) ?: "",
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
            lyrics = firstField(tag, FieldKey.LYRICS),
            replayGainTrackDb = cached?.replayGainTrackDb,
            replayGainAlbumDb = cached?.replayGainAlbumDb
        )
    }

    /**
     * Returns the first value of [key] from [tag], or null when the tag is null,
     * the field is missing, or the value is blank.
     */
    private fun firstField(tag: Tag?, key: FieldKey): String? =
        tag?.let { t ->
            runCatching { t.getFirst(key) }.getOrNull()
                ?.trim()?.takeIf { it.isNotEmpty() }
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
