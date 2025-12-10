package com.example.hida.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hida.data.PreferencesManager
import kotlin.math.*

// =============================================================================
// VAMPIRE CORE THEME COLORS
// =============================================================================
private object VampireColors {
    val Background = Color(0xFF000000)           // True Black
    val Surface = Color(0xFF140508)              // Very dark red-black
    val PrimaryContainer = Color(0xFF781023)     // Vibrant Wine Red (= button)
    val OnPrimaryContainer = Color(0xFFFFFFFF)   // White
    val SecondaryContainer = Color(0xFF181818)   // Dark Grey (numbers)
    val OnSecondaryContainer = Color(0xFFE0E0E0) // Off-white
    val TertiaryContainer = Color(0xFF2B0B11)    // Darkened Red-Brown (operators)
    val OnTertiaryContainer = Color(0xFFFFB4AB)  // Pale Pink
    val FunctionButton = Color(0xFF252525)       // Light Grey (AC, %, ⌫)
    val OnFunctionButton = Color(0xFFD88E96)     // Muted Pink
    val ResultPreview = Color(0xFFFF8996)        // Pink preview text
    val HistoryText = Color(0xFF666666)          // Grey history
}

// Stadium/Pill shape for equals button
private val StadiumShape = RoundedCornerShape(50)

// =============================================================================
// CALCULATOR SCREEN
// =============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorScreen(
    onUnlock: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    // State
    var currentInput by remember { mutableStateOf("0") }
    var resultPreview by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(listOf<String>()) }
    var showAdvanced by remember { mutableStateOf(false) }
    
    // Animation state for result slide
    var showResult by remember { mutableStateOf(false) }
    val transition = updateTransition(targetState = showResult, label = "resultTransition")
    
    val inputAlpha by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.8f, stiffness = 300f) },
        label = "inputAlpha"
    ) { if (it) 0f else 1f }
    
    val inputTranslationY by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.8f, stiffness = 300f) },
        label = "inputTranslationY"
    ) { if (it) -50f else 0f }
    
    val resultScale by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.8f, stiffness = 300f) },
        label = "resultScale"
    ) { if (it) 2.5f else 1f }
    
    val resultTranslationY by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.8f, stiffness = 300f) },
        label = "resultTranslationY"
    ) { if (it) -100f else 0f }

    // Calculate result preview
    LaunchedEffect(currentInput) {
        resultPreview = try {
            val result = evaluateExpression(currentInput)
            if (result != currentInput.toDoubleOrNull()) formatResult(result) else ""
        } catch (e: Exception) { "" }
    }

    fun appendToInput(value: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (currentInput == "0" && value != ".") {
            currentInput = value
        } else if (currentInput == "Error") {
            currentInput = value
        } else {
            currentInput += value
        }
        showResult = false
    }

    fun appendOperator(op: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val lastChar = currentInput.lastOrNull()
        if (lastChar != null && lastChar in "+-×÷") {
            currentInput = currentInput.dropLast(1) + op
        } else {
            currentInput += op
        }
        showResult = false
    }

    fun handleEquals() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        
        // Check for PIN unlock
        val prefs = PreferencesManager(context)
        val realPin = prefs.getPin()
        val fakePin = prefs.getFakePin()
        
        when (currentInput) {
            realPin -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onUnlock(false)
                return
            }
            fakePin -> {
                if (fakePin.isNotEmpty()) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlock(true)
                    return
                }
            }
        }
        
        // Normal calculation with animation
        try {
            val result = evaluateExpression(currentInput)
            val expression = "$currentInput = ${formatResult(result)}"
            history = (listOf(expression) + history).take(20)
            
            showResult = true
            
            // After animation, update input
            currentInput = formatResult(result)
            resultPreview = ""
        } catch (e: Exception) {
            currentInput = "Error"
        }
    }

    fun handleLongPressEquals() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        
        val prefs = PreferencesManager(context)
        val realPin = prefs.getPin()
        val fakePin = prefs.getFakePin()
        
        when (currentInput) {
            realPin -> onUnlock(false)
            fakePin -> if (fakePin.isNotEmpty()) onUnlock(true)
            else -> handleEquals()
        }
    }

    fun clearInput() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentInput = "0"
        resultPreview = ""
        showResult = false
    }

    fun backspace() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentInput = if (currentInput.length > 1) currentInput.dropLast(1) else "0"
        showResult = false
    }

    fun toggleSign() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentInput = if (currentInput.startsWith("-")) currentInput.drop(1) else "-$currentInput"
    }

    fun percent() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        try {
            val value = currentInput.toDouble() / 100
            currentInput = formatResult(value)
        } catch (e: Exception) {}
    }

    // UI
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = VampireColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Display Area (Top 35%)
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // History
                if (history.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        reverseLayout = true
                    ) {
                        items(history) { item ->
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = VampireColors.HistoryText,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Main Input with animation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentInput,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = when {
                                currentInput.length > 12 -> 40.sp
                                currentInput.length > 8 -> 56.sp
                                else -> 80.sp
                            },
                            fontWeight = FontWeight.W300
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                alpha = inputAlpha
                                translationY = inputTranslationY
                            },
                        textAlign = TextAlign.End
                    )
                    
                    // Backspace button
                    IconButton(
                        onClick = { backspace() },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = VampireColors.OnFunctionButton
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
                        color = VampireColors.ResultPreview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = resultScale
                                scaleY = resultScale
                                translationY = resultTranslationY
                            },
                        textAlign = TextAlign.End
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Advanced Keypad Toggle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -20) showAdvanced = true
                            if (dragAmount > 20) showAdvanced = false
                        }
                    }
                    .combinedClickable(
                        onClick = { showAdvanced = !showAdvanced },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                color = VampireColors.Surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = "Toggle Advanced",
                        tint = VampireColors.OnFunctionButton
                    )
                }
            }

            // Advanced Functions (Collapsible)
            AnimatedVisibility(
                visible = showAdvanced,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VampireColors.Surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("sin", "cos", "tan", "π", "√", "^", "!").forEach { func ->
                        VampireAdvancedButton(
                            symbol = func,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                when (func) {
                                    "π" -> appendToInput("3.14159265")
                                    "√" -> currentInput = "√($currentInput)"
                                    "^" -> appendOperator("^")
                                    "!" -> currentInput = "$currentInput!"
                                    else -> currentInput = "$func($currentInput)"
                                }
                            }
                        )
                    }
                }
            }

            // Main Keypad (Bottom 60%)
            Column(
                modifier = Modifier
                    .weight(0.60f)
                    .fillMaxWidth()
                    .background(VampireColors.Surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1: AC, ±, %, ÷
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VampireButton(
                        symbol = "AC",
                        buttonType = VampireButtonType.FUNCTION,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onClick = { clearInput() }
                    )
                    VampireButton(
                        symbol = "±",
                        buttonType = VampireButtonType.FUNCTION,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onClick = { toggleSign() }
                    )
                    VampireButton(
                        symbol = "%",
                        buttonType = VampireButtonType.FUNCTION,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onClick = { percent() }
                    )
                    VampireButton(
                        symbol = "÷",
                        buttonType = VampireButtonType.OPERATOR,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onClick = { appendOperator("÷") }
                    )
                }

                // Row 2: 7, 8, 9, ×
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("7", "8", "9").forEach { num ->
                        VampireButton(
                            symbol = num,
                            buttonType = VampireButtonType.NUMBER,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            onClick = { appendToInput(num) }
                        )
                    }
                    VampireButton(
                        symbol = "×",
                        buttonType = VampireButtonType.OPERATOR,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onClick = { appendOperator("×") }
                    )
                }

                // Row 3: 4, 5, 6, -
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("4", "5", "6").forEach { num ->
                        VampireButton(
                            symbol = num,
                            buttonType = VampireButtonType.NUMBER,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            onClick = { appendToInput(num) }
                        )
                    }
                    VampireButton(
                        symbol = "-",
                        buttonType = VampireButtonType.OPERATOR,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onClick = { appendOperator("-") }
                    )
                }

                // Row 4: 1, 2, 3, +
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("1", "2", "3").forEach { num ->
                        VampireButton(
                            symbol = num,
                            buttonType = VampireButtonType.NUMBER,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            onClick = { appendToInput(num) }
                        )
                    }
                    VampireButton(
                        symbol = "+",
                        buttonType = VampireButtonType.OPERATOR,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onClick = { appendOperator("+") }
                    )
                }

                // Row 5: 0, ., =
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VampireButton(
                        symbol = "0",
                        buttonType = VampireButtonType.NUMBER,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onClick = { appendToInput("0") }
                    )
                    VampireButton(
                        symbol = ".",
                        buttonType = VampireButtonType.NUMBER,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onClick = { if (!currentInput.contains(".")) appendToInput(".") }
                    )
                    // Equals button - Stadium/Pill Shape spanning 2 columns
                    VampireEqualsButton(
                        modifier = Modifier.weight(2f).fillMaxHeight(),
                        onClick = { handleEquals() },
                        onLongClick = { handleLongPressEquals() }
                    )
                }
            }
        }
    }
}

// =============================================================================
// BUTTON TYPES
// =============================================================================
private enum class VampireButtonType {
    NUMBER, OPERATOR, FUNCTION, EQUALS
}

// =============================================================================
// VAMPIRE BUTTON
// =============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VampireButton(
    symbol: String,
    buttonType: VampireButtonType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "buttonScale"
    )

    val backgroundColor = when (buttonType) {
        VampireButtonType.NUMBER -> VampireColors.SecondaryContainer
        VampireButtonType.OPERATOR -> VampireColors.TertiaryContainer
        VampireButtonType.FUNCTION -> VampireColors.FunctionButton
        VampireButtonType.EQUALS -> VampireColors.PrimaryContainer
    }
    
    val textColor = when (buttonType) {
        VampireButtonType.NUMBER -> VampireColors.OnSecondaryContainer
        VampireButtonType.OPERATOR -> VampireColors.OnTertiaryContainer
        VampireButtonType.FUNCTION -> VampireColors.OnFunctionButton
        VampireButtonType.EQUALS -> VampireColors.OnPrimaryContainer
    }

    Surface(
        modifier = modifier
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
                text = symbol,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.W400,
                    fontSize = 28.sp
                ),
                color = textColor
            )
        }
    }
}

// =============================================================================
// VAMPIRE EQUALS BUTTON (Stadium Shape)
// =============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VampireEqualsButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "equalsScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(StadiumShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = VampireColors.PrimaryContainer,
        shape = StadiumShape
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "=",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.W500,
                    fontSize = 36.sp
                ),
                color = VampireColors.OnPrimaryContainer
            )
        }
    }
}

// =============================================================================
// VAMPIRE ADVANCED BUTTON
// =============================================================================
@Composable
private fun VampireAdvancedButton(
    symbol: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "advScale"
    )

    Surface(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        color = VampireColors.Surface,
        shape = CircleShape
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.W400
                ),
                color = VampireColors.OnTertiaryContainer
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
    
    return try {
        evalSimple(cleaned)
    } catch (e: Exception) {
        throw e
    }
}

private fun evalSimple(expr: String): Double {
    var expression = expr.trim()
    
    // Handle factorial
    if (expression.endsWith("!")) {
        val num = expression.dropLast(1).toDouble().toInt()
        return (1..num).fold(1L) { acc, i -> acc * i }.toDouble()
    }
    
    // Handle sqrt
    if (expression.startsWith("√(") && expression.endsWith(")")) {
        val inner = expression.drop(2).dropLast(1)
        return sqrt(evalSimple(inner))
    }
    
    // Handle trig functions
    listOf("sin", "cos", "tan").forEach { func ->
        if (expression.startsWith("$func(") && expression.endsWith(")")) {
            val inner = expression.drop(func.length + 1).dropLast(1)
            val value = Math.toRadians(evalSimple(inner))
            return when (func) {
                "sin" -> sin(value)
                "cos" -> cos(value)
                "tan" -> tan(value)
                else -> evalSimple(inner)
            }
        }
    }
    
    // Handle power
    if (expression.contains("^")) {
        val parts = expression.split("^")
        return parts[0].toDouble().pow(parts[1].toDouble())
    }
    
    // Simple arithmetic
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
                    val num = currentNum.toDouble()
                    result = when (currentOp) {
                        '+' -> result + num
                        '-' -> result - num
                        '*' -> result * num
                        '/' -> result / num
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
