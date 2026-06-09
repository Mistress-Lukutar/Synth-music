package com.synth.synthmusic.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.synth.synthmusic.domain.model.AccentColor

private fun seedColor(accentColor: AccentColor): Color = when (accentColor) {
    AccentColor.YELLOW -> YellowPrimary
    AccentColor.GREEN -> GreenPrimary
    AccentColor.BLUE -> BluePrimary
    AccentColor.RED -> RedPrimary
    AccentColor.PURPLE -> PurplePrimary
    AccentColor.ORANGE -> OrangePrimary
}

private fun ColorScheme.withNeutralSurfaces(isDark: Boolean): ColorScheme {
    val background = if (isDark) DarkBackground else LightBackground
    val surface = if (isDark) DarkSurface else LightSurface
    val surfaceVariant = if (isDark) DarkSurfaceVariant else LightSurfaceVariant
    return copy(
        background = background,
        onBackground = if (isDark) Color(0xFFE6E1E5) else Color(0xFF1C1B1F),
        surface = surface,
        onSurface = if (isDark) Color(0xFFE6E1E5) else Color(0xFF1C1B1F),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F),
        surfaceTint = Color.Transparent,
        inverseSurface = if (isDark) Color(0xFFE6E1E5) else Color(0xFF313033),
        inverseOnSurface = if (isDark) Color(0xFF313033) else Color(0xFFF4EFF4),
        surfaceContainerLowest = if (isDark) Color(0xFF0F0F0F) else Color(0xFFFFFFFF),
        surfaceContainerLow = if (isDark) Color(0xFF181818) else Color(0xFFF8F8F8),
        surfaceContainer = surface,
        surfaceContainerHigh = if (isDark) Color(0xFF282828) else Color(0xFFECECEC),
        surfaceContainerHighest = surfaceVariant,
        surfaceBright = if (isDark) Color(0xFF3A3A3A) else Color(0xFFF8F8F8),
        surfaceDim = if (isDark) Color(0xFF141414) else Color(0xFFDCDCDC)
    )
}

private fun colorScheme(
    darkTheme: Boolean,
    accentColor: AccentColor
): ColorScheme {
    return dynamicColorScheme(
        seedColor = seedColor(accentColor),
        isDark = darkTheme,
        isAmoled = false,
        style = PaletteStyle.TonalSpot,
        modifyColorScheme = { it.withNeutralSurfaces(darkTheme) }
    )
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
        else -> colorScheme(isDark, accentColor ?: AccentColor.YELLOW)
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
