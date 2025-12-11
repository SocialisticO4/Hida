package com.example.hida.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material 3 Dark Theme
 * Seed: #5D0E1D (Deep Burgundy)
 * Background: #050505 (Almost pure black)
 * Surface Container: #000000 (Pure black)
 */

// =============================================================================
// TONAL PALETTE - Burgundy/Deep Red Theme from Seed #5D0E1D
// =============================================================================

// Background / Surface Stack (Pure Dark)
val T0 = Color(0xFF000000)        // Pure black - display area
val T5 = Color(0xFF050505)        // Almost pure black - main background
val T10 = Color(0xFF0D0809)       // Very dark with slight warmth
val T15 = Color(0xFF1A1011)       // Dark burgundy tint
val T20 = Color(0xFF261617)       // Elevated surface
val T25 = Color(0xFF2D1E1F)       // Button surface (numbers)
val T30 = Color(0xFF3A2627)       // Operator button background
val T35 = Color(0xFF452E2F)       // Secondary buttons
val T40 = Color(0xFF533839)       // Higher elevation

// Primary - Burgundy (AC Button) from seed #5D0E1D
val Primary40 = Color(0xFF5D0E1D)  // Seed color - deep burgundy
val Primary50 = Color(0xFF8B1E30)  // Lighter burgundy
val Primary60 = Color(0xFFB0384C)  // Accent burgundy
val Primary70 = Color(0xFFCF5A68)  // Light burgundy
val Primary80 = Color(0xFFE88A95)  // Very light burgundy

// Secondary - Dark muted burgundy (Operator buttons)
val Secondary30 = Color(0xFF3A2627) // Dark muted for operators
val Secondary40 = Color(0xFF4D3536) // Slightly lighter

// Tertiary - Warm rose/pink (Equals Button)
val Tertiary70 = Color(0xFFE8A0A8)  // Soft pink/rose 
val Tertiary80 = Color(0xFFF4C4C8)  // Light rose (equals button)
val Tertiary90 = Color(0xFFFFE0E2)  // Very light rose

// Text Colors
val OnSurfaceHigh = Color(0xFFE8E0E0)   // Warm white text
val OnSurfaceMedium = Color(0xFFB0A5A5) // Warm grey text
val CursorHighlight = Color(0xFFE88A95) // Burgundy accent cursor

// =============================================================================
// SURFACE CONTAINERS (For MD3 compatibility)
// =============================================================================
val Surface = T0                          // Pure black
val SurfaceContainerLowest = T0           // Pure black
val SurfaceContainerLow = T5              // Almost black
val SurfaceContainer = T15                // Very dark
val SurfaceContainerHigh = T30            // Operators
val SurfaceContainerHighest = T25         // Numbers

// Primary colors for MD3
val PrimaryContainer = Primary50          // AC button
val OnPrimaryContainer = Color.White
val SecondaryContainer = Primary50        // AC button (same as primary)
val OnSecondaryContainer = Color.White

// Text on surface
val OnSurface = OnSurfaceHigh
val OnSurfaceVariant = OnSurfaceMedium

// =============================================================================
// MD3 COLOR ROLES (Dark Theme) - Burgundy Seed
// =============================================================================

// Primary - Burgundy accent (AC button)
val md3_dark_primary = Primary60
val md3_dark_onPrimary = Color.White
val md3_dark_primaryContainer = Primary50
val md3_dark_onPrimaryContainer = Color.White

// Secondary - Dark muted (operator buttons ÷ × - +)
val md3_dark_secondary = Secondary40
val md3_dark_onSecondary = OnSurfaceHigh
val md3_dark_secondaryContainer = T30
val md3_dark_onSecondaryContainer = OnSurfaceHigh

// Tertiary - Warm rose (equals button)
val md3_dark_tertiary = Tertiary80
val md3_dark_onTertiary = Color.Black
val md3_dark_tertiaryContainer = Tertiary70
val md3_dark_onTertiaryContainer = Color.Black

// Error
val md3_dark_error = Color(0xFFCF6679)
val md3_dark_onError = Color.Black
val md3_dark_errorContainer = Color(0xFF4A1919)
val md3_dark_onErrorContainer = Color(0xFFFFB4AB)

// Background & Surface - Pure black
val md3_dark_background = T5              // #050505
val md3_dark_onBackground = OnSurfaceHigh
val md3_dark_surface = T0                 // #000000 
val md3_dark_onSurface = OnSurfaceHigh
val md3_dark_surfaceVariant = T20
val md3_dark_onSurfaceVariant = OnSurfaceMedium

// Outline
val md3_dark_outline = T40
val md3_dark_outlineVariant = T30

// Inverse
val md3_dark_inverseSurface = OnSurfaceHigh
val md3_dark_inverseOnSurface = T0
val md3_dark_inversePrimary = Primary40

// Scrim & Shadow
val md3_dark_scrim = T0
val md3_dark_shadow = T0
val md3_dark_surfaceTint = Primary60

// Surface containers - All dark with burgundy tint
val md3_dark_surfaceContainerLowest = T0     // Pure black (#000000)
val md3_dark_surfaceContainerLow = T5        // Almost black (#050505)
val md3_dark_surfaceContainer = T15          // Very dark
val md3_dark_surfaceContainerHigh = T25      // Number button background
val md3_dark_surfaceContainerHighest = T35   // Elevated