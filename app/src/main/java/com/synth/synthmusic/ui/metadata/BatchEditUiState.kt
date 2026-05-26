package com.synth.synthmusic.ui.metadata

import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.usecase.BatchUpdateMetadataUseCase

/**
 * UI state for the batch metadata editor.
 *
 * @param songs the songs selected for batch editing.
 * @param title title override state.
 * @param artist artist override state.
 * @param album album override state.
 * @param albumArtist album artist override state.
 * @param genre genre override state.
 * @param year year override state.
 * @param trackNumber track number override state.
 * @param comment comment override state.
 * @param isSaving true while metadata is being written.
 */
data class BatchEditUiState(
    val songs: List<Song> = emptyList(),
    val title: BatchUpdateMetadataUseCase.FieldOverride = BatchUpdateMetadataUseCase.FieldOverride(),
    val artist: BatchUpdateMetadataUseCase.FieldOverride = BatchUpdateMetadataUseCase.FieldOverride(),
    val album: BatchUpdateMetadataUseCase.FieldOverride = BatchUpdateMetadataUseCase.FieldOverride(),
    val albumArtist: BatchUpdateMetadataUseCase.FieldOverride = BatchUpdateMetadataUseCase.FieldOverride(),
    val genre: BatchUpdateMetadataUseCase.FieldOverride = BatchUpdateMetadataUseCase.FieldOverride(),
    val year: BatchUpdateMetadataUseCase.FieldOverride = BatchUpdateMetadataUseCase.FieldOverride(),
    val trackNumber: BatchUpdateMetadataUseCase.FieldOverride = BatchUpdateMetadataUseCase.FieldOverride(),
    val comment: BatchUpdateMetadataUseCase.FieldOverride = BatchUpdateMetadataUseCase.FieldOverride(),
    val isSaving: Boolean = false
)
