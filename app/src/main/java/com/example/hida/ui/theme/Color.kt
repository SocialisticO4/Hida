package com.example.hida.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * VAMPIRE CORE Theme Colors
 * Seed Color: #5D0E1D (Deep Wine Red)
 */

// =============================================================================
// VAMPIRE CORE PALETTE
// =============================================================================

// Background & Surface
val VampireBackground = Color(0xFF000000)        // True Black
val VampireSurface = Color(0xFF140508)           // Very dark red-black
val VampireSurfaceHigh = Color(0xFF1C080C)       // Slightly elevated surface

// Primary (Wine Red)
val VampirePrimary = Color(0xFF781023)           // Vibrant Wine Red
val VampireOnPrimary = Color(0xFFFFFFFF)         // White
val VampirePrimaryContainer = Color(0xFF781023)
val VampireOnPrimaryContainer = Color(0xFFFFFFFF)

// Secondary (Dark Grey for Numbers)
val VampireSecondary = Color(0xFF181818)         // Dark Grey
val VampireOnSecondary = Color(0xFFE0E0E0)       // Off-white
val VampireSecondaryContainer = Color(0xFF181818)
val VampireOnSecondaryContainer = Color(0xFFE0E0E0)

// Tertiary (Operators)
val VampireTertiary = Color(0xFF2B0B11)          // Darkened Red-Brown
val VampireOnTertiary = Color(0xFFFFB4AB)        // Pale Pink
val VampireTertiaryContainer = Color(0xFF2B0B11)
val VampireOnTertiaryContainer = Color(0xFFFFB4AB)

// Function Buttons
val VampireFunction = Color(0xFF252525)          // Light Grey
val VampireOnFunction = Color(0xFFD88E96)        // Muted Pink

// Accent Colors
val VampireResultPreview = Color(0xFFFF8996)     // Pink preview text
val VampireHistory = Color(0xFF666666)           // Grey history

// Error
val VampireError = Color(0xFFCF6679)
val VampireOnError = Color(0xFF000000)
val VampireErrorContainer = Color(0xFF3D0A0A)
val VampireOnErrorContainer = Color(0xFFFFB4AB)

// Outline
val VampireOutline = Color(0xFF444444)
val VampireOutlineVariant = Color(0xFF2A2A2A)

// =============================================================================
// MD3 COLOR ROLES (Dark Theme)
// =============================================================================
val md3_dark_primary = VampirePrimary
val md3_dark_onPrimary = VampireOnPrimary
val md3_dark_primaryContainer = VampirePrimaryContainer
val md3_dark_onPrimaryContainer = VampireOnPrimaryContainer

val md3_dark_secondary = VampireSecondary
val md3_dark_onSecondary = VampireOnSecondary
val md3_dark_secondaryContainer = VampireSecondaryContainer
val md3_dark_onSecondaryContainer = VampireOnSecondaryContainer

val md3_dark_tertiary = VampireTertiary
val md3_dark_onTertiary = VampireOnTertiary
val md3_dark_tertiaryContainer = VampireTertiaryContainer
val md3_dark_onTertiaryContainer = VampireOnTertiaryContainer

val md3_dark_error = VampireError
val md3_dark_onError = VampireOnError
val md3_dark_errorContainer = VampireErrorContainer
val md3_dark_onErrorContainer = VampireOnErrorContainer

val md3_dark_background = VampireBackground
val md3_dark_onBackground = Color(0xFFE0E0E0)
val md3_dark_surface = VampireSurface
val md3_dark_onSurface = Color(0xFFE0E0E0)
val md3_dark_surfaceVariant = VampireSurfaceHigh
val md3_dark_onSurfaceVariant = Color(0xFFAAAAAA)

val md3_dark_outline = VampireOutline
val md3_dark_outlineVariant = VampireOutlineVariant

val md3_dark_inverseSurface = Color(0xFFE0E0E0)
val md3_dark_inverseOnSurface = VampireBackground
val md3_dark_inversePrimary = Color(0xFF5D0E1D)

val md3_dark_scrim = VampireBackground
val md3_dark_shadow = VampireBackground
val md3_dark_surfaceTint = VampirePrimary

// Surface containers
val md3_dark_surfaceContainerLowest = Color(0xFF000000)
val md3_dark_surfaceContainerLow = Color(0xFF0A0305)
val md3_dark_surfaceContainer = VampireSurface
val md3_dark_surfaceContainerHigh = VampireSurfaceHigh
val md3_dark_surfaceContainerHighest = Color(0xFF241014)