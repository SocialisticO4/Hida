package com.example.hida.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
import com.example.hida.ui.theme.*

@Composable
fun CalculatorScreen(
    onUnlock: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var displayText by remember { mutableStateOf("0") }
    var subDisplayText by remember { mutableStateOf("") }
    
    // Calculator state
    var firstOperand by remember { mutableStateOf<Double?>(null) }
    var pendingOperator by remember { mutableStateOf<String?>(null) }
    var waitingForSecondOperand by remember { mutableStateOf(false) }

    fun calculate(op1: Double, op2: Double, operator: String): Double {
        return when (operator) {
            "+" -> op1 + op2
            "-" -> op1 - op2
            "×" -> op1 * op2
            "÷" -> if (op2 != 0.0) op1 / op2 else Double.NaN
            else -> op2
        }
    }

    fun formatResult(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.8f", value).trimEnd('0').trimEnd('.')
        }
    }

    HidaTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PureBlack,
                            SurfaceBlack
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .systemBarsPadding(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Display Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End
                ) {
                    // Sub display (expression)
                    if (subDisplayText.isNotEmpty()) {
                        Text(
                            text = subDisplayText,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextTertiary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    // Main display
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = when {
                                displayText.length > 12 -> 36.sp
                                displayText.length > 8 -> 48.sp
                                else -> 72.sp
                            },
                            fontWeight = FontWeight.W200
                        ),
                        color = TextPrimary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Keypad with Material 3 Expressive Design
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(SurfaceElevated.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val buttonRows = listOf(
                        listOf("C", "±", "%", "÷"),
                        listOf("7", "8", "9", "×"),
                        listOf("4", "5", "6", "-"),
                        listOf("1", "2", "3", "+"),
                        listOf("0", ".", "=")
                    )

                    buttonRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { label ->
                                val weight = if (label == "0") 2f else 1f
                                val buttonType = when {
                                    label in listOf("÷", "×", "-", "+", "=") -> ButtonType.OPERATION
                                    label in listOf("C", "±", "%") -> ButtonType.FUNCTION
                                    else -> ButtonType.NUMBER
                                }
                                
                                ExpressiveCalculatorButton(
                                    symbol = label,
                                    buttonType = buttonType,
                                    modifier = Modifier
                                        .weight(weight)
                                        .aspectRatio(if (label == "0") 2.1f else 1f),
                                    onClick = {
                                        when (label) {
                                            "C" -> {
                                                displayText = "0"
                                                subDisplayText = ""
                                                firstOperand = null
                                                pendingOperator = null
                                                waitingForSecondOperand = false
                                            }
                                            "±" -> {
                                                if (displayText != "0" && displayText != "Error") {
                                                    displayText = if (displayText.startsWith("-")) {
                                                        displayText.substring(1)
                                                    } else {
                                                        "-$displayText"
                                                    }
                                                }
                                            }
                                            "%" -> {
                                                val value = displayText.toDoubleOrNull()
                                                if (value != null) {
                                                    displayText = formatResult(value / 100)
                                                }
                                            }
                                            "=" -> {
                                                val prefs = PreferencesManager(context)
                                                val realPin = prefs.getPin()
                                                val fakePin = prefs.getFakePin()
                                                
                                                // Check for PIN unlock first
                                                if (displayText == realPin) {
                                                    onUnlock(false) // Real Mode
                                                } else if (fakePin.isNotEmpty() && displayText == fakePin) {
                                                    onUnlock(true) // Fake Mode
                                                } else {
                                                    // Perform actual calculation
                                                    val currentValue = displayText.toDoubleOrNull()
                                                    if (currentValue != null && firstOperand != null && pendingOperator != null) {
                                                        val result = calculate(firstOperand!!, currentValue, pendingOperator!!)
                                                        subDisplayText = "${formatResult(firstOperand!!)} $pendingOperator ${formatResult(currentValue)} ="
                                                        displayText = if (result.isNaN()) "Error" else formatResult(result)
                                                        firstOperand = null
                                                        pendingOperator = null
                                                        waitingForSecondOperand = false
                                                    }
                                                }
                                            }
                                            in listOf("+", "-", "×", "÷") -> {
                                                val currentValue = displayText.toDoubleOrNull()
                                                if (currentValue != null) {
                                                    if (firstOperand != null && pendingOperator != null && !waitingForSecondOperand) {
                                                        val result = calculate(firstOperand!!, currentValue, pendingOperator!!)
                                                        displayText = formatResult(result)
                                                        firstOperand = result
                                                    } else {
                                                        firstOperand = currentValue
                                                    }
                                                    pendingOperator = label
                                                    waitingForSecondOperand = true
                                                }
                                            }
                                            "." -> {
                                                if (!displayText.contains(".")) {
                                                    displayText += "."
                                                }
                                            }
                                            else -> {
                                                // Number input
                                                if (displayText == "0" || displayText == "Error" || waitingForSecondOperand) {
                                                    displayText = label
                                                    waitingForSecondOperand = false
                                                } else {
                                                    displayText += label
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class ButtonType {
    NUMBER, OPERATION, FUNCTION
}

@Composable
fun ExpressiveCalculatorButton(
    symbol: String,
    buttonType: ButtonType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Spring animation for press effect
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when (buttonType) {
            ButtonType.OPERATION -> if (isPressed) BlackCherryLight else BlackCherry
            ButtonType.FUNCTION -> if (isPressed) SurfaceContainerHigh else SurfaceContainer
            ButtonType.NUMBER -> if (isPressed) SurfaceContainerHigh else SurfaceElevated
        },
        label = "backgroundColor"
    )

    val contentColor = when (buttonType) {
        ButtonType.OPERATION -> TextPrimary
        ButtonType.FUNCTION -> BlackCherryLight
        ButtonType.NUMBER -> TextPrimary
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(if (symbol == "0") RoundedCornerShape(32.dp) else CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        color = backgroundColor,
        shape = if (symbol == "0") RoundedCornerShape(32.dp) else CircleShape,
        tonalElevation = if (buttonType == ButtonType.OPERATION) 8.dp else 2.dp
    ) {
        Box(
            contentAlignment = if (symbol == "0") Alignment.CenterStart else Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (symbol == "0") PaddingValues(start = 28.dp) else PaddingValues(0.dp))
        ) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = if (buttonType == ButtonType.OPERATION) FontWeight.W600 else FontWeight.W400
                ),
                color = contentColor
            )
        }
    }
}
