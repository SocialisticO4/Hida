package com.example.hida.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Material Design 3 Dark Color Scheme
 * Seed: #5D0E1D (Deep Burgundy)
 * Background: #050505 | Surface: #000000
 */
private val MD3DarkColorScheme = darkColorScheme(
    // Primary
    primary = md3_dark_primary,
    onPrimary = md3_dark_onPrimary,
    primaryContainer = md3_dark_primaryContainer,
    onPrimaryContainer = md3_dark_onPrimaryContainer,
    
    // Secondary
    secondary = md3_dark_secondary,
    onSecondary = md3_dark_onSecondary,
    secondaryContainer = md3_dark_secondaryContainer,
    onSecondaryContainer = md3_dark_onSecondaryContainer,
    
    // Tertiary
    tertiary = md3_dark_tertiary,
    onTertiary = md3_dark_onTertiary,
    tertiaryContainer = md3_dark_tertiaryContainer,
    onTertiaryContainer = md3_dark_onTertiaryContainer,
    
    // Error
    error = md3_dark_error,
    onError = md3_dark_onError,
    errorContainer = md3_dark_errorContainer,
    onErrorContainer = md3_dark_onErrorContainer,
    
    // Background & Surface
    background = md3_dark_background,
    onBackground = md3_dark_onBackground,
    surface = md3_dark_surface,
    onSurface = md3_dark_onSurface,
    surfaceVariant = md3_dark_surfaceVariant,
    onSurfaceVariant = md3_dark_onSurfaceVariant,
    
    // Outline
    outline = md3_dark_outline,
    outlineVariant = md3_dark_outlineVariant,
    
    // Inverse
    inverseSurface = md3_dark_inverseSurface,
    inverseOnSurface = md3_dark_inverseOnSurface,
    inversePrimary = md3_dark_inversePrimary,
    
    // Scrim
    scrim = md3_dark_scrim,

    // Surface tint
    surfaceTint = md3_dark_surfaceTint,

    // Surface containers
    surfaceContainerLowest = md3_dark_surfaceContainerLowest,
    surfaceContainerLow = md3_dark_surfaceContainerLow,
    surfaceContainer = md3_dark_surfaceContainer,
    surfaceContainerHigh = md3_dark_surfaceContainerHigh,
    surfaceContainerHighest = md3_dark_surfaceContainerHighest
)

@Composable
fun HidaTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use transparent status/nav bars for edge-to-edge
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = MD3DarkColorScheme,
        typography = HidaTypography,
        shapes = HidaShapes,
        content = content
    )
}