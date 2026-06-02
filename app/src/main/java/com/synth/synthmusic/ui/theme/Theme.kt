package com.synth.synthmusic.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.synth.synthmusic.domain.model.AccentColor

private fun accentColorScheme(
    darkTheme: Boolean,
    accentColor: AccentColor
): androidx.compose.material3.ColorScheme {
    val (primary, secondary, tertiary) = when (accentColor) {
        AccentColor.YELLOW -> Triple(YellowPrimary, YellowSecondary, YellowTertiary)
        AccentColor.GREEN -> Triple(GreenPrimary, GreenSecondary, GreenTertiary)
        AccentColor.BLUE -> Triple(BluePrimary, BlueSecondary, BlueTertiary)
        AccentColor.RED -> Triple(RedPrimary, RedSecondary, RedTertiary)
        AccentColor.PURPLE -> Triple(PurplePrimary, PurpleSecondary, PurpleTertiary)
        AccentColor.ORANGE -> Triple(OrangePrimary, OrangeSecondary, OrangeTertiary)
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            background = DarkBackground,
            surface = DarkSurface,
            surfaceVariant = DarkSurfaceVariant
        )
    } else {
        lightColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            background = LightBackground,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant
        )
    }
}

/**
 * Root theme for the application.
 *
 * @param darkTheme Whether to use dark mode. Null follows system setting.
 * @param accentColor Accent color override. Defaults to yellow.
 * @param dynamicColor Whether to use dynamic color on Android 12+.
 * @param content Root composable content.
 */
@Composable
/**
 * SynthMusicTheme implementation.
 */
/**
 * SynthMusicTheme.
 */
fun SynthMusicTheme(
    darkTheme: Boolean? = null,
    accentColor: AccentColor? = AccentColor.YELLOW,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = darkTheme ?: isSystemInDarkTheme()
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> accentColorScheme(isDark, accentColor ?: AccentColor.YELLOW)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
