package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Premium Obsidian Charcoal Palette (extremely clean, dark, high contrast)
val CinemaBackground = Color(0xFF0B0C0E) // Deepest pure charcoal black
val CinemaSurface = Color(0xFF15171A) // Elegant dark slate card surface
val CinemaSurfaceVariant = Color(0xFF22252A) // Medium subtle slate/border variant

// Dynamic Theme Color States
object AppThemeState {
    var primaryColor by mutableStateOf(Color(0xFFFFB300))
    var secondaryColor by mutableStateOf(Color(0xFFFFD54F))
}

// Dynamic properties referencing current active theme states
val NeonCyan: Color
    get() = AppThemeState.primaryColor

val BrightCyan: Color
    get() = AppThemeState.secondaryColor

val NeonMagenta = Color(0xFFFF4B5C) // A stunning modern coral/rose red accent (perfect for deletions/favorite hearts)
val NeonGreen = Color(0xFF00E676) // Vivid electric emerald green for status indicators/active states

// Light theme alternatives (No longer utilized, but set to dark for compatibility)
val CinemaPrimaryLight = Color(0xFFFFB300)
val CinemaBackgroundLight = Color(0xFF0B0C0E)
val CinemaSurfaceLight = Color(0xFF15171A)
