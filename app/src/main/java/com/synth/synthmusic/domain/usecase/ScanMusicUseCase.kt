package com.synth.synthmusic.domain.usecase

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.synth.synthmusic.data.local.cover.CoverCache
import com.synth.synthmusic.data.local.database.WaveformDataDao
import com.synth.synthmusic.data.media.waveform.WaveformPreloader
import com.synth.synthmusic.domain.model.Album
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.AlbumRepository
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.id3.AbstractID3v2Frame

/**
 * Use case for scanning device storage and indexing MP3 files into the local database.
 */
class ScanMusicUseCase(
    private val context: Context,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val waveformPreloader: WaveformPreloader,
    private val waveformDataDao: WaveformDataDao,
    private val coverCache: CoverCache
) {

    suspend operator fun invoke(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val songs = scanSongs()
            songRepository.saveSongs(songs)

            waveformPreloader.preload(songs)
            waveformDataDao.deleteOrphaned()

            val albums = deriveAlbums(songs)
            albumRepository.saveAlbums(albums)

            val artists = deriveArtists(songs, albums)
            artistRepository.saveArtists(artists)

            songs.size
        }
    }

    @SuppressLint("InlinedApi")
    private fun scanSongs(): List<Song> {
        val resolver = context.contentResolver
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

        val songs = mutableListOf<Song>()
        resolver.query(
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
                val id = cursor.getLong(idCol)
                val path = cursor.getString(dataCol) ?: continue
                val uri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                ).toString()

                val retriever = MediaMetadataRetriever()
                var bitrate = 0
                var sampleRate = 0
                var genre = ""
                var lyrics: String? = null
                var artworkBytes: ByteArray? = null
                try {
                    retriever.setDataSource(context, Uri.parse(uri))
                    bitrate = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_BITRATE
                    )?.toIntOrNull()?.div(1000) ?: 0
                    sampleRate = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_SAMPLERATE
                    )?.toIntOrNull() ?: 0
                    genre = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_GENRE
                    ) ?: ""
                    lyrics = null
                    artworkBytes = retriever.embeddedPicture
                } catch (_: Exception) {
                    // ignore corrupted files
                } finally {
                    retriever.release()
                }

                var replayGainTrackDb: Float? = null
                var replayGainAlbumDb: Float? = null
                try {
                    val audioFile = AudioFileIO.read(java.io.File(path))
                    val tag = audioFile.tag
                    if (tag != null) {
                        tag.getFields("TXXX").forEach { field ->
                            val frame = field as? AbstractID3v2Frame ?: return@forEach
                            val body = frame.body
                            val description = try {
                                body.getObjectValue("Description") as? String
                            } catch (_: Exception) { null }
                            val text = try {
                                body.getObjectValue("Text") as? String
                            } catch (_: Exception) { null }
                            when (description) {
                                "REPLAYGAIN_TRACK_GAIN" -> {
                                    replayGainTrackDb = text?.replace(" dB", "")?.toFloatOrNull()
                                }
                                "REPLAYGAIN_ALBUM_GAIN" -> {
                                    replayGainAlbumDb = text?.replace(" dB", "")?.toFloatOrNull()
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // ignore tag read errors
                }

                val artworkUri = artworkBytes?.let {
                    val file = coverCache.saveSongArtwork(id.toString(), it)
                    Uri.fromFile(file).toString()
                } ?: run {
                    // Fallback to MediaStore album art thumbnail
                    try {
                        val mediaStoreUri = Uri.parse("content://media/external/audio/media/$id/albumart")
                        resolver.openInputStream(mediaStoreUri)?.use { input ->
                            val bytes = input.readBytes()
                            val file = coverCache.saveSongArtwork(id.toString(), bytes)
                            Uri.fromFile(file).toString()
                        }
                    } catch (_: Exception) {
                        null
                    }
                }

                songs.add(
                    Song(
                        id = id.toString(),
                        title = cursor.getString(titleCol) ?: "Unknown Title",
                        artist = cursor.getString(artistCol) ?: "Unknown Artist",
                        album = cursor.getString(albumCol) ?: "Unknown Album",
                        albumArtist = cursor.getString(albumArtistCol)
                            ?: cursor.getString(artistCol) ?: "Unknown Artist",
                        durationMs = cursor.getLong(durationCol),
                        trackNumber = cursor.getInt(trackCol),
                        year = cursor.getInt(yearCol),
                        genre = genre,
                        comment = "",
                        path = path,
                        uri = uri,
                        bitrate = bitrate,
                        sampleRate = sampleRate,
                        fileSize = cursor.getLong(sizeCol),
                        artworkUri = artworkUri,
                        rating = 0f,
                        playCount = 0,
                        lastPlayed = null,
                        dateAdded = cursor.getLong(addedCol) * 1000,
                        dateModified = cursor.getLong(modifiedCol) * 1000,
                        lyrics = lyrics,
                        replayGainTrackDb = replayGainTrackDb,
                        replayGainAlbumDb = replayGainAlbumDb
                    )
                )
            }
        }
        return songs
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
