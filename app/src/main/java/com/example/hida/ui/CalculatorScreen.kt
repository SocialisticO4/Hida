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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.hida.data.PreferencesManager
import com.example.hida.ui.theme.*

@Composable
fun CalculatorScreen(
    onUnlock: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var isDarkTheme by remember { mutableStateOf(true) }
    var displayText by remember { mutableStateOf("0") }
    var subDisplayText by remember { mutableStateOf("") } // For showing history like "12:6="
    
    // Temporary state for calculator logic
    var currentNumber by remember { mutableStateOf("") }
    var operand1 by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }

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
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.End,
                        lineHeight = 80.sp
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
                        listOf("0", ",", "=")
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
                                                currentNumber = ""
                                                operand1 = ""
                                                operator = ""
                                            }
                                            "=" -> {
                                                val prefs = PreferencesManager(context)
                                                val realPin = prefs.getPin()
                                                val fakePin = prefs.getFakePin()
                                                
                                                if (displayText == realPin) {
                                                    onUnlock(false) // Real Mode
                                                } else if (fakePin.isNotEmpty() && displayText == fakePin) {
                                                    onUnlock(true) // Fake Mode
                                                } else {
                                                    // Basic mock calculation logic
                                                    // In a real app, use a proper expression parser
                                                    subDisplayText = "$displayText="
                                                    displayText = "Error" // Placeholder for logic
                                                }
                                            }
                                            else -> {
                                                if (displayText == "0") displayText = label else displayText += label
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
            .clickable { onClick() },
        color = backgroundColor,
        shape = if (symbol == "0") RoundedCornerShape(40.dp) else CircleShape,
        shadowElevation = 6.dp // Add shadow for "pop" effect
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
