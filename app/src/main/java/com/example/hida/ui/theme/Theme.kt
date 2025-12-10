package com.example.hida.ui.theme

import android.app.Activity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Material 3 Expressive Dark Color Scheme - Pure Black AMOLED
private val ExpressiveDarkScheme = darkColorScheme(
    primary = BlackCherry,
    onPrimary = TextPrimary,
    primaryContainer = BlackCherryDark,
    onPrimaryContainer = TextPrimary,
    
    secondary = AccentRedDim,
    onSecondary = TextPrimary,
    secondaryContainer = SurfaceContainer,
    onSecondaryContainer = TextPrimary,
    
    tertiary = BlackCherryLight,
    onTertiary = TextPrimary,
    tertiaryContainer = SurfaceContainerHigh,
    onTertiaryContainer = TextPrimary,
    
    background = PureBlack,
    onBackground = TextPrimary,
    
    surface = SurfaceBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    
    surfaceTint = SurfaceTint,
    
    error = ErrorRed,
    onError = TextPrimary,
    errorContainer = Color(0xFF3D0A0A),
    onErrorContainer = TextPrimary,
    
    outline = TextTertiary,
    outlineVariant = SurfaceContainer,
    
    inverseSurface = TextPrimary,
    inverseOnSurface = PureBlack,
    inversePrimary = BlackCherryDark,
    
    scrim = PureBlack
)

// Motion specs for Material 3 Expressive
object ExpressiveMotion {
    val FastSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    val MediumSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    
    val SlowSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessVeryLow
    )
}

@Composable
fun HidaTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = ExpressiveDarkScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.navigationBarColor = PureBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ExpressiveTypography,
        content = content
    )
}