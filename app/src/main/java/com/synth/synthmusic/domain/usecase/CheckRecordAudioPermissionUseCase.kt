package com.synth.synthmusic.domain.usecase

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Checks whether the app has been granted [Manifest.permission.RECORD_AUDIO].
 *
 * This permission is required for the system [android.media.audiofx.Visualizer]
 * to capture audio output for visualisation.
 */
class CheckRecordAudioPermissionUseCase(
    private val context: Context
) {
    operator fun invoke(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
