package com.synth.synthmusic.domain.usecase

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

/**
 * Checks whether the app has the necessary permission to write to external MP3 files.
 *
 * On API 29+ this requires [MANAGE_EXTERNAL_STORAGE].
 * On API 28 and below [WRITE_EXTERNAL_STORAGE] is sufficient.
 */
class CheckWritePermissionUseCase(
    private val context: Context
) {
    operator fun invoke(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
