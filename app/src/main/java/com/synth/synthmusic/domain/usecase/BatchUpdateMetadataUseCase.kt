package com.synth.synthmusic.domain.usecase

import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Use case for updating metadata across multiple songs in a single operation.
 *
 * Only fields explicitly marked as [FieldOverride.enabled] are written.
 * All other fields retain their original values per song.
 *
 * @param songRepository the repository for persisting song data.
 * @param updateMetadataUseCase the single-song metadata update delegate.
 */
class BatchUpdateMetadataUseCase(
    private val songRepository: SongRepository,
    private val updateMetadataUseCase: UpdateMetadataUseCase
) {

    /**
     * Represents a metadata field and whether it should be overwritten.
     *
     * @param value the new value to apply.
     * @param enabled true if this field should be updated for all selected songs.
     */
    data class FieldOverride(
        val value: String = "",
        val enabled: Boolean = false
    )

    /**
     * Batch update metadata for the provided songs.
     *
     * @param songs the songs to update.
     * @param title title override.
     * @param artist artist override.
     * @param album album override.
     * @param albumArtist album artist override.
     * @param genre genre override.
     * @param year year override.
     * @param trackNumber track number override.
     * @param comment comment override.
     */
    suspend operator fun invoke(
        songs: List<Song>,
        title: FieldOverride = FieldOverride(),
        artist: FieldOverride = FieldOverride(),
        album: FieldOverride = FieldOverride(),
        albumArtist: FieldOverride = FieldOverride(),
        genre: FieldOverride = FieldOverride(),
        year: FieldOverride = FieldOverride(),
        trackNumber: FieldOverride = FieldOverride(),
        comment: FieldOverride = FieldOverride()
    ) = withContext(Dispatchers.IO) {
        songs.forEach { song ->
            updateMetadataUseCase(
                song = song,
                title = title.value.takeIf { title.enabled },
                artist = artist.value.takeIf { artist.enabled },
                album = album.value.takeIf { album.enabled },
                albumArtist = albumArtist.value.takeIf { albumArtist.enabled },
                genre = genre.value.takeIf { genre.enabled },
                year = year.value.takeIf { year.enabled },
                trackNumber = trackNumber.value.takeIf { trackNumber.enabled },
                comment = comment.value.takeIf { comment.enabled }
            )
        }
    }
}
