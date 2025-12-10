package com.example.hida.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Shape System
 */
val HidaShapes = Shapes(
    // Extra Small - Chips, small buttons
    extraSmall = RoundedCornerShape(4.dp),
    
    // Small - Cards, dialogs
    small = RoundedCornerShape(8.dp),
    
    // Medium - FABs, navigation items
    medium = RoundedCornerShape(16.dp),
    
    // Large - Large cards, sheets
    large = RoundedCornerShape(24.dp),
    
    // Extra Large - Full-screen dialogs, large sheets
    extraLarge = RoundedCornerShape(32.dp)
)

// Custom shapes for calculator buttons
val SquircleShape = RoundedCornerShape(28.dp)
val CircleButtonShape = RoundedCornerShape(50)
