package com.synth.synthmusic.ui.metadata

/**
 * User events for the batch metadata editor.
 */
sealed class BatchEditEvent {

    /**
     * Toggles whether a field should be overwritten for all selected songs.
     */
    data class ToggleField(val field: MetadataField, val enabled: Boolean) : BatchEditEvent()

    /**
     * Updates the value of a field.
     */
    data class UpdateValue(val field: MetadataField, val value: String) : BatchEditEvent()

    /**
     * Triggers the batch save operation.
     */
    data object Save : BatchEditEvent()
}

/**
 * Enum representing editable metadata fields.
 */
enum class MetadataField {
    TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, YEAR, TRACK_NUMBER, COMMENT
}
