package com.example.hida.ui

import androidx.compose.animation.core.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hida.data.PreferencesManager
import kotlinx.coroutines.delay
import kotlin.math.*

// =============================================================================
// M3 EXPRESSIVE TONAL PALETTE
// =============================================================================
private object CalcColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF1A0F10)
    val SurfaceVariant = Color(0xFF2C1C1D)
    
    // Number buttons - warm dark tones
    val NumberBg = Color(0xFF3D2B2C)
    val NumberText = Color(0xFFE8D9D9)
    
    // Operator buttons
    val OperatorBg = Color(0xFF524646)
    val OperatorText = Color(0xFFD8C3C3)
    
    // AC button - Primary accent
    val AcBg = Color(0xFF7A332C)
    val AcText = Color(0xFFE8D9D9)
    
    // Equals button - Tertiary gold
    val EqualsBg = Color(0xFFD1B270)
    val EqualsText = Color(0xFF000000)
    
    // Cursor and result preview
    val Cursor = Color(0xFFE2B4B7)
    val ResultPreview = Color(0xFFD1B270)
    val HistoryText = Color(0xFF888888)
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
    
    // State
    var expression by remember { mutableStateOf("0") }
    var resultPreview by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    
    // Blinking cursor state
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(900)
            cursorVisible = !cursorVisible
        }
    }

    // Calculate live result preview
    LaunchedEffect(expression) {
        resultPreview = try {
            if (expression.isNotEmpty() && expression != "0" && expression != "Error") {
                val result = evaluateExpression(expression)
                val formatted = formatResult(result)
                if (formatted != expression) formatted else ""
            } else ""
        } catch (e: Exception) { "" }
    }

    fun append(value: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        expression = when {
            expression == "0" && value != "." -> value
            expression == "Error" -> value
            else -> expression + value
        }
    }

    fun appendOperator(op: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val lastChar = expression.lastOrNull()
        expression = when {
            expression == "0" && op == "-" -> "-"
            expression == "Error" -> "0"
            lastChar != null && lastChar in "+-×÷" -> expression.dropLast(1) + op
            else -> expression + op
        }
    }

    fun handleEquals() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        
        // Check PIN unlock
        val prefs = PreferencesManager(context)
        when (expression) {
            prefs.getPin() -> { onUnlock(false); return }
            prefs.getFakePin() -> if (prefs.getFakePin().isNotEmpty()) { onUnlock(true); return }
        }
        
        // Calculate
        try {
            val result = evaluateExpression(expression)
            expression = formatResult(result)
            resultPreview = ""
        } catch (e: Exception) {
            expression = "Error"
        }
    }

    fun clear() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        expression = "0"
        resultPreview = ""
    }

    fun backspace() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        expression = when {
            expression.length <= 1 -> "0"
            expression == "Error" -> "0"
            else -> expression.dropLast(1)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CalcColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { }) {
                    Icon(Icons.Default.History, "History", tint = CalcColors.OperatorText)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu", tint = CalcColors.OperatorText)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = CalcColors.SurfaceVariant,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        listOf("Clear history", "Settings").forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item, color = CalcColors.NumberText) },
                                onClick = { showMenu = false }
                            )
                        }
                    }
                }
            }

            // Display Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                // Expression with cursor
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = expression,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = when {
                                expression.length > 14 -> 36.sp
                                expression.length > 10 -> 48.sp
                                expression.length > 7 -> 56.sp
                                else -> 72.sp
                            },
                            fontWeight = FontWeight.W300,
                            letterSpacing = 1.sp
                        ),
                        color = CalcColors.NumberText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    
                    // Cursor
                    if (cursorVisible) {
                        Box(
                            modifier = Modifier
                                .padding(start = 2.dp, bottom = 8.dp)
                                .width(3.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(CalcColors.Cursor)
                        )
                    }
                }
                
                // Result Preview
                if (resultPreview.isNotEmpty()) {
                    Text(
                        text = "= $resultPreview",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.W300
                        ),
                        color = CalcColors.ResultPreview,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(56.dp))
                }
            }

            // Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: AC, (), %, ÷
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalcButton("AC", CalcColors.AcBg, CalcColors.AcText, Modifier.weight(1f)) { clear() }
                    CalcButton("( )", CalcColors.OperatorBg, CalcColors.OperatorText, Modifier.weight(1f)) { append("()") }
                    CalcButton("%", CalcColors.OperatorBg, CalcColors.OperatorText, Modifier.weight(1f)) { append("%") }
                    CalcButton("÷", CalcColors.OperatorBg, CalcColors.OperatorText, Modifier.weight(1f)) { appendOperator("÷") }
                }
                
                // Row 2: 7, 8, 9, ×
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalcButton("7", CalcColors.NumberBg, CalcColors.NumberText, Modifier.weight(1f)) { append("7") }
                    CalcButton("8", CalcColors.NumberBg, CalcColors.NumberText, Modifier.weight(1f)) { append("8") }
                    CalcButton("9", CalcColors.NumberBg, CalcColors.NumberText, Modifier.weight(1f)) { append("9") }
                    CalcButton("×", CalcColors.OperatorBg, CalcColors.OperatorText, Modifier.weight(1f)) { appendOperator("×") }
                }
                
                // Row 3: 4, 5, 6, -
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalcButton("4", CalcColors.NumberBg, CalcColors.NumberText, Modifier.weight(1f)) { append("4") }
                    CalcButton("5", CalcColors.NumberBg, CalcColors.NumberText, Modifier.weight(1f)) { append("5") }
                    CalcButton("6", CalcColors.NumberBg, CalcColors.NumberText, Modifier.weight(1f)) { append("6") }
                    CalcButton("−", CalcColors.OperatorBg, CalcColors.OperatorText, Modifier.weight(1f)) { appendOperator("-") }
                }
                
                // Row 4: 1, 2, 3, +
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalcButton("1", CalcColors.NumberBg, CalcColors.NumberText, Modifier.weight(1f)) { append("1") }
                    CalcButton("2", CalcColors.NumberBg, CalcColors.NumberText, Modifier.weight(1f)) { append("2") }
                    CalcButton("3", CalcColors.NumberBg, CalcColors.NumberText, Modifier.weight(1f)) { append("3") }
                    CalcButton("+", CalcColors.OperatorBg, CalcColors.OperatorText, Modifier.weight(1f)) { appendOperator("+") }
                }
                
                // Row 5: 0, ., ⌫, =
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalcButton("0", CalcColors.NumberBg, CalcColors.NumberText, Modifier.weight(1f)) { append("0") }
                    CalcButton(".", CalcColors.NumberBg, CalcColors.NumberText, Modifier.weight(1f)) { 
                        if (!expression.contains(".") || expression.any { it in "+-×÷" }) append(".") 
                    }
                    BackspaceButton(CalcColors.OperatorBg, CalcColors.NumberText, Modifier.weight(1f)) { backspace() }
                    CalcButton("=", CalcColors.EqualsBg, CalcColors.EqualsText, Modifier.weight(1f)) { handleEquals() }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// =============================================================================
// CALC BUTTON
// =============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalcButton(
    label: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "scale"
    )

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        color = bgColor,
        shape = CircleShape
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = when (label) {
                        "AC" -> 28.sp
                        "( )" -> 24.sp
                        else -> 32.sp
                    },
                    fontWeight = if (label in listOf("AC", "=")) FontWeight.W600 else FontWeight.W400
                ),
                color = textColor
            )
        }
    }
}

// =============================================================================
// BACKSPACE BUTTON
// =============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackspaceButton(
    bgColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "scale"
    )

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        color = bgColor,
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
    
    // Handle multiplication and division first
    var result = 0.0
    var currentOp = '+'
    var currentNum = ""
    var terms = mutableListOf<Pair<Char, Double>>()
    
    for (char in "$expression+") {
        when {
            char.isDigit() || char == '.' || (char == '-' && currentNum.isEmpty()) -> {
                currentNum += char
            }
            char in "+-*/" -> {
                if (currentNum.isNotEmpty()) {
                    terms.add(Pair(currentOp, currentNum.toDoubleOrNull() ?: 0.0))
                    currentNum = ""
                }
                currentOp = char
            }
        }
    }
    
    // Process multiplication and division
    var i = 0
    while (i < terms.size) {
        val (op, num) = terms[i]
        if (op == '*' || op == '/') {
            val prev = terms[i - 1].second
            val newVal = if (op == '*') prev * num else if (num != 0.0) prev / num else Double.NaN
            terms[i - 1] = Pair(terms[i - 1].first, newVal)
            terms.removeAt(i)
        } else {
            i++
        }
    }
    
    // Process addition and subtraction
    for ((op, num) in terms) {
        result = when (op) {
            '+' -> result + num
            '-' -> result - num
            else -> result
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
