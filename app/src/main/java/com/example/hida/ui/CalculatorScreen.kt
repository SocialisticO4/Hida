package com.example.hida.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hida.data.PreferencesManager
import kotlinx.coroutines.delay
import kotlin.math.*

// =============================================================================
// MATERIAL 3 EXPRESSIVE TONAL PALETTE
// =============================================================================
private object M3Tones {
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
    
    // Text / Cursor
    val OnSurfaceHigh = Color(0xFFE8D9D9)
    val OnSurfaceMedium = Color(0xFFD8C3C3)
    val CursorHighlight = Color(0xFFE2B4B7)
}

// =============================================================================
// CALCULATOR SCREEN
// =============================================================================
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onUnlock: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Button diameter: 21-22% of screen width
    val buttonDiameter = screenWidth * 0.21f
    val buttonSpacing = 12.dp
    
    // State
    var expression by remember { mutableStateOf("") }
    var secondaryResult by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    
    // Blinking cursor
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(900)
            cursorVisible = !cursorVisible
        }
    }

    // Calculate secondary result
    LaunchedEffect(expression) {
        secondaryResult = try {
            if (expression.isNotEmpty()) {
                val result = evaluateExpression(expression)
                formatResult(result)
            } else ""
        } catch (e: Exception) { "" }
    }

    fun appendToExpression(value: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        expression += value
    }

    fun handleEquals() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        
        // Check for PIN unlock
        val prefs = PreferencesManager(context)
        val realPin = prefs.getPin()
        val fakePin = prefs.getFakePin()
        
        when (expression) {
            realPin -> { onUnlock(false); return }
            fakePin -> if (fakePin.isNotEmpty()) { onUnlock(true); return }
        }
        
        // Normal calculation
        try {
            val result = evaluateExpression(expression)
            expression = formatResult(result)
            secondaryResult = ""
        } catch (e: Exception) {
            expression = "Error"
        }
    }

    fun clear() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        expression = ""
        secondaryResult = ""
    }

    fun backspace() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (expression.isNotEmpty()) expression = expression.dropLast(1)
    }

    // UI
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = M3Tones.T0
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // ⭐ TOOLBAR ZONE (Row 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* History */ }) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "History",
                        tint = M3Tones.OnSurfaceMedium
                    )
                }
                
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = M3Tones.OnSurfaceMedium
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = M3Tones.T22,
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        listOf("Clear history", "Choose theme", "Privacy policy", "Send feedback", "Help").forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        item,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 28.sp),
                                        color = M3Tones.OnSurfaceHigh
                                    )
                                },
                                onClick = {
                                    if (item == "Clear history") showClearDialog = true
                                    showMenu = false
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                            )
                        }
                    }
                }
            }

            // ⭐ EXPRESSION ZONE (40-45% of screen)
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxWidth()
                    .padding(end = 24.dp, top = 20.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                // Expression with cursor
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = expression.ifEmpty { "0" },
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = when {
                                expression.length > 12 -> 40.sp
                                expression.length > 8 -> 50.sp
                                else -> 60.sp
                            },
                            fontWeight = FontWeight.W400
                        ),
                        color = M3Tones.OnSurfaceHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    
                    // Blinking cursor
                    Box(
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .width(4.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (cursorVisible) M3Tones.CursorHighlight
                                else Color.Transparent
                            )
                    )
                }
                
                // Secondary result
                if (secondaryResult.isNotEmpty() && secondaryResult != expression) {
                    Text(
                        text = "= $secondaryResult",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.W400
                        ),
                        color = M3Tones.Tertiary80,
                        modifier = Modifier.padding(top = 12.dp),
                        textAlign = TextAlign.End
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Scroll indicator (chevron)
                Icon(
                    Icons.Default.ExpandLess,
                    contentDescription = null,
                    tint = M3Tones.OnSurfaceMedium.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // ⭐ KEYPAD GRID (5 rows × 4 columns)
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Row 1: AC, (), %, ÷
                KeypadRow(buttonDiameter, buttonSpacing) {
                    M3Button("AC", M3Tones.Primary60, M3Tones.OnSurfaceHigh, buttonDiameter, true) { clear() }
                    M3Button("()", M3Tones.T45, M3Tones.OnSurfaceMedium, buttonDiameter) { appendToExpression("()") }
                    M3Button("%", M3Tones.T50, M3Tones.OnSurfaceMedium, buttonDiameter) { appendToExpression("%") }
                    M3Button("÷", M3Tones.T50, M3Tones.OnSurfaceMedium, buttonDiameter) { appendToExpression("÷") }
                }
                
                // Row 2: 7, 8, 9, ×
                KeypadRow(buttonDiameter, buttonSpacing) {
                    M3Button("7", M3Tones.T40, M3Tones.OnSurfaceHigh, buttonDiameter) { appendToExpression("7") }
                    M3Button("8", M3Tones.T45, M3Tones.OnSurfaceHigh, buttonDiameter) { appendToExpression("8") }
                    M3Button("9", M3Tones.T50, M3Tones.OnSurfaceHigh, buttonDiameter) { appendToExpression("9") }
                    M3Button("×", M3Tones.T50, M3Tones.OnSurfaceMedium, buttonDiameter) { appendToExpression("×") }
                }
                
                // Row 3: 4, 5, 6, −
                KeypadRow(buttonDiameter, buttonSpacing) {
                    M3Button("4", M3Tones.T40, M3Tones.OnSurfaceHigh, buttonDiameter) { appendToExpression("4") }
                    M3Button("5", M3Tones.T45, M3Tones.OnSurfaceHigh, buttonDiameter) { appendToExpression("5") }
                    M3Button("6", M3Tones.T50, M3Tones.OnSurfaceHigh, buttonDiameter) { appendToExpression("6") }
                    M3Button("−", M3Tones.T50, M3Tones.OnSurfaceMedium, buttonDiameter) { appendToExpression("-") }
                }
                
                // Row 4: 1, 2, 3, +
                KeypadRow(buttonDiameter, buttonSpacing) {
                    M3Button("1", M3Tones.T40, M3Tones.OnSurfaceHigh, buttonDiameter) { appendToExpression("1") }
                    M3Button("2", M3Tones.T45, M3Tones.OnSurfaceHigh, buttonDiameter) { appendToExpression("2") }
                    M3Button("3", M3Tones.T50, M3Tones.OnSurfaceHigh, buttonDiameter) { appendToExpression("3") }
                    M3Button("+", M3Tones.T50, M3Tones.OnSurfaceMedium, buttonDiameter) { appendToExpression("+") }
                }
                
                // Row 5: 0, ., ⌫, =
                KeypadRow(buttonDiameter, buttonSpacing) {
                    M3Button("0", M3Tones.T40, M3Tones.OnSurfaceHigh, buttonDiameter) { appendToExpression("0") }
                    M3Button(".", M3Tones.T45, M3Tones.OnSurfaceHigh, buttonDiameter) { appendToExpression(".") }
                    M3BackspaceButton(M3Tones.T50, M3Tones.OnSurfaceHigh, buttonDiameter) { backspace() }
                    M3Button("=", M3Tones.Tertiary80, Color.Black, buttonDiameter, true) { handleEquals() }
                }
            }
        }

        // Clear Dialog
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                containerColor = M3Tones.T10,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        "Clear history?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = M3Tones.OnSurfaceHigh
                    )
                },
                text = {
                    Text(
                        "This will permanently delete all calculation history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = M3Tones.OnSurfaceMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Clear", color = M3Tones.Tertiary80)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Dismiss", color = M3Tones.OnSurfaceMedium)
                    }
                }
            )
        }
    }
}

// =============================================================================
// KEYPAD ROW
// =============================================================================
@Composable
private fun KeypadRow(
    buttonDiameter: Dp,
    spacing: Dp,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// =============================================================================
// M3 BUTTON (Perfect Circle)
// =============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun M3Button(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    diameter: Dp,
    isEmphasized: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Scale animation: 0.94× on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) 90 else 120,
            easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
        ),
        label = "buttonScale"
    )

    Surface(
        modifier = Modifier
            .size(diameter)
            .scale(scale)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        color = backgroundColor,
        shape = CircleShape
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = if (label == "AC" || label == "=") 40.sp else 44.sp,
                    fontWeight = if (isEmphasized) FontWeight.W600 else FontWeight.W500,
                    letterSpacing = if (label.length == 1 && label[0].isDigit()) 0.5.sp else 1.sp
                ),
                color = textColor
            )
        }
    }
}

// =============================================================================
// M3 BACKSPACE BUTTON
// =============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun M3BackspaceButton(
    backgroundColor: Color,
    iconColor: Color,
    diameter: Dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) 90 else 120,
            easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
        ),
        label = "backspaceScale"
    )

    Surface(
        modifier = Modifier
            .size(diameter)
            .scale(scale)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        color = backgroundColor,
        shape = CircleShape
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace",
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// =============================================================================
// EXPRESSION EVALUATOR
// =============================================================================
private fun evaluateExpression(expr: String): Double {
    val cleaned = expr
        .replace("×", "*")
        .replace("÷", "/")
        .replace("−", "-")
        .replace("()", "")
        .replace("%", "/100")
    
    return evalSimple(cleaned)
}

private fun evalSimple(expr: String): Double {
    var expression = expr.trim()
    if (expression.isEmpty()) return 0.0
    
    var result = 0.0
    var currentOp = '+'
    var currentNum = ""
    
    for (char in "$expression+") {
        when {
            char.isDigit() || char == '.' || (char == '-' && currentNum.isEmpty()) -> {
                currentNum += char
            }
            char in "+-*/" -> {
                if (currentNum.isNotEmpty()) {
                    val num = currentNum.toDoubleOrNull() ?: 0.0
                    result = when (currentOp) {
                        '+' -> result + num
                        '-' -> result - num
                        '*' -> result * num
                        '/' -> if (num != 0.0) result / num else Double.NaN
                        else -> result
                    }
                    currentNum = ""
                }
                currentOp = char
            }
        }
    }
    
    return result
}

private fun formatResult(value: Double): String {
    return if (value == value.toLong().toDouble() && abs(value) < 1e15) {
        value.toLong().toString()
    } else {
        String.format("%.10f", value).trimEnd('0').trimEnd('.')
    }
}
