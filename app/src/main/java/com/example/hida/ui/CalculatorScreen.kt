package com.example.hida.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hida.data.PreferencesManager
import com.example.hida.ui.theme.HidaShapes
import com.example.hida.ui.theme.SquircleShape
import kotlinx.coroutines.delay
import kotlin.math.abs

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
    
    // Blinking cursor
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(900)
            cursorVisible = !cursorVisible
        }
    }

    // Calculate preview
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
        
        val prefs = PreferencesManager(context)
        when (expression) {
            prefs.getPin() -> { onUnlock(false); return }
            prefs.getFakePin() -> if (prefs.getFakePin().isNotEmpty()) { onUnlock(true); return }
        }
        
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.History,
                            "History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                "Menu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            shape = HidaShapes.large
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear history") },
                                onClick = { showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = { showMenu = false }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Display Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                // Expression
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = expression,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = when {
                                expression.length > 12 -> 44.sp
                                expression.length > 8 -> 56.sp
                                else -> 64.sp
                            },
                            fontWeight = FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    
                    if (cursorVisible) {
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp, bottom = 10.dp)
                                .width(3.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        )
                    }
                }
                
                // Result Preview
                AnimatedVisibility(visible = resultPreview.isNotEmpty()) {
                    Text(
                        text = "= $resultPreview",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )
                }
                
                if (resultPreview.isEmpty()) {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }

            // Keypad Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CalculatorButton(
                        text = "AC",
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { clear() },
                        modifier = Modifier.weight(1f)
                    )
                    CalculatorButton(
                        text = "( )",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { append("()") },
                        modifier = Modifier.weight(1f)
                    )
                    CalculatorButton(
                        text = "%",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { append("%") },
                        modifier = Modifier.weight(1f)
                    )
                    CalculatorButton(
                        text = "÷",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { appendOperator("÷") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CalculatorButton("7", onClick = { append("7") }, modifier = Modifier.weight(1f))
                    CalculatorButton("8", onClick = { append("8") }, modifier = Modifier.weight(1f))
                    CalculatorButton("9", onClick = { append("9") }, modifier = Modifier.weight(1f))
                    CalculatorButton(
                        text = "×",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { appendOperator("×") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 3
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CalculatorButton("4", onClick = { append("4") }, modifier = Modifier.weight(1f))
                    CalculatorButton("5", onClick = { append("5") }, modifier = Modifier.weight(1f))
                    CalculatorButton("6", onClick = { append("6") }, modifier = Modifier.weight(1f))
                    CalculatorButton(
                        text = "−",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { appendOperator("-") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 4
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CalculatorButton("1", onClick = { append("1") }, modifier = Modifier.weight(1f))
                    CalculatorButton("2", onClick = { append("2") }, modifier = Modifier.weight(1f))
                    CalculatorButton("3", onClick = { append("3") }, modifier = Modifier.weight(1f))
                    CalculatorButton(
                        text = "+",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { appendOperator("+") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 5
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CalculatorButton("0", onClick = { append("0") }, modifier = Modifier.weight(1f))
                    CalculatorButton(".", onClick = { append(".") }, modifier = Modifier.weight(1f))
                    CalculatorButton(
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { backspace() },
                        modifier = Modifier.weight(1f)
                    )
                    CalculatorButton(
                        text = "=",
                        color = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        onClick = { handleEquals() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorButton(
    text: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SquircleShape // Using Squircle for Expressive feel
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "scale"
    )

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        color = color,
        contentColor = contentColor,
        shape = shape
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = if (text in listOf("AC", "=")) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// Expression evaluator logic...
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
    
    // Multiplication and division
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
    
    // Addition and subtraction
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
