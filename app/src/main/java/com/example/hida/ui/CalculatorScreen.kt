package com.example.hida.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.example.hida.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorScreen(
    onUnlock: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
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

    fun handleEquals() {
        val prefs = PreferencesManager(context)
        val realPin = prefs.getPin()
        val fakePin = prefs.getFakePin()
        
        // Check for PIN unlock first
        when (displayText) {
            realPin -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onUnlock(false) // Real Mode
                return
            }
            fakePin -> {
                if (fakePin.isNotEmpty()) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlock(true) // Fake Mode
                    return
                }
            }
        }
        
        // Normal calculation if PIN doesn't match
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

    HidaTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    // Main display
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = when {
                                displayText.length > 12 -> 40.sp
                                displayText.length > 8 -> 56.sp
                                else -> 72.sp
                            },
                            fontWeight = FontWeight.W200
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Keypad
                Column(
                    modifier = Modifier
                        .clip(HidaShapes.extraLarge)
                        .background(md3_dark_surfaceContainer)
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
                                val buttonType = when (label) {
                                    "=" -> ButtonType.PRIMARY
                                    in listOf("÷", "×", "-", "+") -> ButtonType.SECONDARY
                                    in listOf("C", "±", "%") -> ButtonType.TERTIARY
                                    else -> ButtonType.SURFACE
                                }
                                
                                MD3CalculatorButton(
                                    symbol = label,
                                    buttonType = buttonType,
                                    modifier = Modifier
                                        .weight(weight)
                                        .aspectRatio(if (label == "0") 2.2f else 1f),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                            "=" -> handleEquals()
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
                                                if (displayText == "0" || displayText == "Error" || waitingForSecondOperand) {
                                                    displayText = label
                                                    waitingForSecondOperand = false
                                                } else {
                                                    displayText += label
                                                }
                                            }
                                        }
                                    },
                                    onLongClick = if (label == "=") {
                                        { handleLongPressEquals() }
                                    } else null
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
    PRIMARY,    // = button (glowing red)
    SECONDARY,  // Operators (÷×-+)
    TERTIARY,   // Functions (C, ±, %)
    SURFACE     // Numbers (0-9, .)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MD3CalculatorButton(
    symbol: String,
    buttonType: ButtonType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale"
    )

    val containerColor = when (buttonType) {
        ButtonType.PRIMARY -> MaterialTheme.colorScheme.primary
        ButtonType.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
        ButtonType.TERTIARY -> MaterialTheme.colorScheme.surfaceVariant
        ButtonType.SURFACE -> md3_dark_surfaceContainerHigh
    }
    
    val contentColor = when (buttonType) {
        ButtonType.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        ButtonType.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
        ButtonType.TERTIARY -> MaterialTheme.colorScheme.onSurfaceVariant
        ButtonType.SURFACE -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(if (symbol == "0") SquircleShape else CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = containerColor,
        shape = if (symbol == "0") SquircleShape else CircleShape,
        tonalElevation = if (buttonType == ButtonType.PRIMARY) 6.dp else 2.dp
    ) {
        Box(
            contentAlignment = if (symbol == "0") Alignment.CenterStart else Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (symbol == "0") PaddingValues(start = 32.dp) else PaddingValues(0.dp))
        ) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = when (buttonType) {
                        ButtonType.PRIMARY -> FontWeight.W600
                        ButtonType.SECONDARY -> FontWeight.W500
                        else -> FontWeight.W400
                    }
                ),
                color = contentColor
            )
        }
    }
}
