package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val CinemaDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CinemaBackground,
    secondary = BrightCyan,
    onSecondary = CinemaBackground,
    tertiary = NeonMagenta,
    onTertiary = CinemaBackground,
    background = CinemaBackground,
    onBackground = CinemaBackgroundLight,
    surface = CinemaSurface,
    onSurface = CinemaBackgroundLight,
    surfaceVariant = CinemaSurfaceVariant,
    onSurfaceVariant = BrightCyan
)

private val CinemaLightColorScheme = CinemaDarkColorScheme // Ensure light fallback is identical to dark scheme

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force default to dark theme
    dynamicColor: Boolean = false, // Force disable dynamic colors to preserve customized brand personality
    content: @Composable () -> Unit,
) {
    // Force custom premium obsidian dark theme exclusively
    val colorScheme = CinemaDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
