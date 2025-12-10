package com.example.hida.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hida.data.PreferencesManager
import com.example.hida.ui.theme.*

@Composable
fun CalculatorScreen(
    onUnlock: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var isDarkTheme by remember { mutableStateOf(true) }
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

    HidaTheme(darkTheme = isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar with Theme Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    ThemeToggle(isDark = isDarkTheme, onToggle = { isDarkTheme = !isDarkTheme })
                }

                // Display Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = displayText,
                        fontSize = if (displayText.length > 10) 50.sp else 80.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.End,
                        lineHeight = 80.sp,
                        maxLines = 1
                    )
                    if (subDisplayText.isNotEmpty()) {
                        Text(
                            text = subDisplayText,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.End
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val buttonRows = listOf(
                        listOf("C", "+/-", "%", "÷"),
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
                                val isOperation = label in listOf("÷", "×", "-", "+", "=")
                                val isFunction = label in listOf("C", "+/-", "%")
                                
                                CalculatorButton(
                                    symbol = label,
                                    modifier = Modifier
                                        .weight(weight)
                                        .aspectRatio(if (label == "0") 2f else 1f),
                                    isOperation = isOperation,
                                    isFunction = isFunction,
                                    onClick = {
                                        when (label) {
                                            "C" -> {
                                                displayText = "0"
                                                subDisplayText = ""
                                                firstOperand = null
                                                pendingOperator = null
                                                waitingForSecondOperand = false
                                            }
                                            "+/-" -> {
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
                                                        subDisplayText = "$firstOperand $pendingOperator $currentValue ="
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

@Composable
fun ThemeToggle(isDark: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA))
            .clickable { onToggle() }
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LightMode,
                contentDescription = "Light Mode",
                tint = if (!isDark) Color.Black else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = "Dark Mode",
                tint = if (isDark) Color.White else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun CalculatorButton(
    symbol: String,
    modifier: Modifier = Modifier,
    isOperation: Boolean = false,
    isFunction: Boolean = false,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val backgroundColor = when {
        isOperation -> MaterialTheme.colorScheme.tertiary
        isFunction -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surface
    }
    
    val contentColor = when {
        isOperation -> MaterialTheme.colorScheme.onTertiary
        isFunction -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .clip(if (symbol == "0") RoundedCornerShape(40.dp) else CircleShape)
            .clickable { 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick() 
            },
        color = backgroundColor,
        shape = if (symbol == "0") RoundedCornerShape(40.dp) else CircleShape,
        shadowElevation = 6.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = symbol,
                fontSize = 32.sp,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
