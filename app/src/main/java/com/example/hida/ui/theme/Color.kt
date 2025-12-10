package com.example.hida.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material 3 Expressive Tonal Palette
 * Warm-neutral reds, warm browns, low-contrast surface layers
 */

// =============================================================================
// TONAL PALETTE (Exact Values)
// =============================================================================

// Background / Surface Stack
val T0 = Color(0xFF000000)
val T10 = Color(0xFF1A0F10)
val T20 = Color(0xFF2C1C1D)
val T22 = Color(0xFF2A1C1C)
val T30 = Color(0xFF3D2B2C)
val T40 = Color(0xFF4A3E3D)
val T45 = Color(0xFF524646)
val T50 = Color(0xFF5E4C4B)

// Primary (AC Button)
val Primary60 = Color(0xFF7A332C)

// Tertiary (Equals Button)
val Tertiary80 = Color(0xFFD1B270)

// Text / Cursor Colors
val OnSurfaceHigh = Color(0xFFE8D9D9)
val OnSurfaceMedium = Color(0xFFD8C3C3)
val CursorHighlight = Color(0xFFE2B4B7)

// =============================================================================
// MD3 COLOR ROLES (Dark Theme)
// =============================================================================
val md3_dark_primary = Primary60
val md3_dark_onPrimary = OnSurfaceHigh
val md3_dark_primaryContainer = Primary60
val md3_dark_onPrimaryContainer = OnSurfaceHigh

val md3_dark_secondary = T45
val md3_dark_onSecondary = OnSurfaceHigh
val md3_dark_secondaryContainer = T40
val md3_dark_onSecondaryContainer = OnSurfaceHigh

val md3_dark_tertiary = Tertiary80
val md3_dark_onTertiary = Color.Black
val md3_dark_tertiaryContainer = T50
val md3_dark_onTertiaryContainer = OnSurfaceMedium

val md3_dark_error = Color(0xFFCF6679)
val md3_dark_onError = Color.Black
val md3_dark_errorContainer = T30
val md3_dark_onErrorContainer = Color(0xFFFFB4AB)

val md3_dark_background = T0
val md3_dark_onBackground = OnSurfaceHigh
val md3_dark_surface = T10
val md3_dark_onSurface = OnSurfaceHigh
val md3_dark_surfaceVariant = T20
val md3_dark_onSurfaceVariant = OnSurfaceMedium

val md3_dark_outline = T40
val md3_dark_outlineVariant = T30

val md3_dark_inverseSurface = OnSurfaceHigh
val md3_dark_inverseOnSurface = T0
val md3_dark_inversePrimary = Color(0xFF5D0E1D)

val md3_dark_scrim = T0
val md3_dark_shadow = T0
val md3_dark_surfaceTint = Primary60

// Surface containers
val md3_dark_surfaceContainerLowest = T0
val md3_dark_surfaceContainerLow = T10
val md3_dark_surfaceContainer = T20
val md3_dark_surfaceContainerHigh = T30
val md3_dark_surfaceContainerHighest = T40