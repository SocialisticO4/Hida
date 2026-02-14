package com.example.hida.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ripple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hida.R
import com.example.hida.ui.theme.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onUnlock: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val prefs = LocalPreferencesManager.current
    
    var expression by remember { mutableStateOf("") }
    var previousExpression by remember { mutableStateOf("") }
    var previousResult by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var isDegMode by remember { mutableStateOf(true) }
    var isInvMode by remember { mutableStateOf(false) }
    
    // Brute force protection
    var lockoutRemainingMs by remember { mutableStateOf(prefs.getRemainingLockoutTime()) }
    var showLockoutWarning by remember { mutableStateOf(false) }
    
    // Update lockout timer
    LaunchedEffect(lockoutRemainingMs) {
        if (lockoutRemainingMs > 0) {
            kotlinx.coroutines.delay(1000)
            lockoutRemainingMs = prefs.getRemainingLockoutTime()
        }
    }

    fun formatWithCommas(num: String): String {
        if (num.isEmpty() || num == "-") return num
        val parts = num.split(".")
        val intPart = parts[0].replace(",", "")
        if (intPart.isEmpty()) return num
        val sb = StringBuilder()
        var count = 0
        for (i in intPart.length - 1 downTo 0) {
            if (intPart[i] == '-') sb.insert(0, '-')
            else {
                if (count > 0 && count % 3 == 0) sb.insert(0, ',')
                sb.insert(0, intPart[i])
                count++
            }
        }
        return if (parts.size > 1) "$sb.${parts[1]}" else sb.toString()
    }

    fun formatExpr(expr: String): String {
        if (expr.isEmpty()) return "0"
        if (expr == "Error") return "Error"
        val result = StringBuilder()
        var numBuffer = StringBuilder()
        for (c in expr) {
            if (c.isDigit() || c == '.') numBuffer.append(c)
            else {
                if (numBuffer.isNotEmpty()) { result.append(formatWithCommas(numBuffer.toString())); numBuffer.clear() }
                result.append(c)
            }
        }
        if (numBuffer.isNotEmpty()) result.append(formatWithCommas(numBuffer.toString()))
        return result.toString()
    }

    val displayText = formatExpr(expression)

    fun append(value: String) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        expression = when {
            expression == "Error" -> value
            else -> expression + value
        }
    }

    fun appendOp(op: String) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val last = expression.lastOrNull()
        expression = when {
            expression.isEmpty() && op == "-" -> "-"
            expression == "Error" -> "0"
            last in listOf('+', '-', '×', '÷') -> expression.dropLast(1) + op
            else -> expression + op
        }
    }

    fun clear() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        expression = ""
        previousExpression = ""
        previousResult = ""
    }

    fun backspace() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        expression = if (expression.length <= 1) "" else expression.dropLast(1)
    }

    fun evalExpr(expr: String): Double {
        var e = expr.replace(",", "").trim()
        if (e.isEmpty()) return 0.0
        val terms = mutableListOf<Pair<Char, Double>>()
        var op = '+'
        var num = ""
        for (c in "$e+") {
            when {
                c.isDigit() || c == '.' || (c == '-' && num.isEmpty()) -> num += c
                c in "+-*/" -> {
                    if (num.isNotEmpty()) { terms.add(op to (num.toDoubleOrNull() ?: 0.0)); num = "" }
                    op = c
                }
            }
        }
        var i = 0
        while (i < terms.size) {
            val (o, n) = terms[i]
            if (o == '*' || o == '/') {
                val prev = terms[i - 1].second
                terms[i - 1] = terms[i - 1].first to (if (o == '*') prev * n else if (n != 0.0) prev / n else Double.NaN)
                terms.removeAt(i)
            } else i++
        }
        return terms.fold(0.0) { acc, (o, n) -> if (o == '+') acc + n else acc - n }
    }

    fun formatResult(v: Double): String {
        return if (v == v.toLong().toDouble() && abs(v) < 1e12) v.toLong().toString()
        else String.format("%.8f", v).trimEnd('0').trimEnd('.')
    }

    fun equals() {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        
        // Check if locked out
        if (prefs.isLockedOut()) {
            lockoutRemainingMs = prefs.getRemainingLockoutTime()
            showLockoutWarning = true
            return
        }
        
        val userPin = prefs.getPin()
        val fakePin = prefs.getFakePin()
        
        when (expression) {
            userPin -> { 
                prefs.clearFailedAttempts()
                prefs.clearSession() // Start fresh session on login
                onUnlock(false)
                return 
            }
            fakePin -> if (fakePin.isNotEmpty()) { 
                prefs.clearFailedAttempts()
                onUnlock(true)
                return 
            }
        }
        
        // Check if this looks like a PIN attempt (4-10 digits, no operators)
        val isPinAttempt = expression.length in 4..10 && 
                           expression.all { it.isDigit() } &&
                           userPin != null
        
        if (isPinAttempt) {
            // Wrong PIN - record failed attempt
            val lockoutDuration = prefs.recordFailedAttempt()
            if (lockoutDuration > 0) {
                lockoutRemainingMs = lockoutDuration
                showLockoutWarning = true
            }
        }
        
        // Normal calculation
        try {
            val expr = expression.replace("×", "*").replace("÷", "/")
            val result = evalExpr(expr)
            if (result.isNaN()) expression = "Error"
            else {
                previousExpression = expression
                previousResult = formatWithCommas(formatResult(result))
                expression = formatResult(result)
            }
        } catch (e: Exception) { expression = "Error" }
    }

    // Button shape changes based on expanded state
    // Circle when collapsed (1:1), Stadium/Pill when expanded (wider than tall)
    val buttonShape: Shape = if (expanded) RoundedCornerShape(50) else CircleShape

    // Lockout warning dialog
    if (showLockoutWarning) {
        AlertDialog(
            onDismissRequest = { showLockoutWarning = false },
            title = { Text(stringResource(R.string.lockout_title)) },
            text = {
                val seconds = (lockoutRemainingMs / 1000).toInt()
                Text(stringResource(R.string.lockout_message, seconds))
            },
            confirmButton = {
                TextButton(onClick = { showLockoutWarning = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    Scaffold(
        containerColor = T5,
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = { }) { Icon(Icons.Outlined.History, "History", tint = OnSurfaceMedium) } },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, "Menu", tint = OnSurfaceMedium) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = T30, shape = RoundedCornerShape(16.dp)) {
                            DropdownMenuItem(text = { Text("Clear history", color = OnSurfaceHigh) }, onClick = { showMenu = false })
                            DropdownMenuItem(text = { Text("Choose theme", color = OnSurfaceHigh) }, onClick = { showMenu = false })
                            DropdownMenuItem(text = { Text("Privacy policy", color = OnSurfaceHigh) }, onClick = { showMenu = false })
                            DropdownMenuItem(text = { Text("Send feedback", color = OnSurfaceHigh) }, onClick = { showMenu = false })
                            DropdownMenuItem(text = { Text("Help", color = OnSurfaceHigh) }, onClick = { showMenu = false })
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp)
        ) {
            // DISPLAY - Takes remaining space, shrinks when expanded
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    if (previousExpression.isNotEmpty()) {
                        Text(formatExpr(previousExpression), fontSize = 16.sp, color = OnSurfaceMedium, fontFamily = Roboto, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        Text(previousResult, fontSize = 20.sp, color = OnSurfaceMedium, fontFamily = Roboto, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    }
                    Text(
                        text = displayText,
                        fontFamily = Roboto,
                        fontSize = when { displayText.length > 10 -> 48.sp; displayText.length > 6 -> 64.sp; else -> 72.sp },
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Toggle Arrow - Points down when collapsed, up when expanded
            Box(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 4.dp), contentAlignment = Alignment.CenterStart) {
                IconButton(onClick = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Toggle Scientific",
                        tint = OnSurfaceMedium
                    )
                }
            }

            // Scientific Panel - Slides down from top, fades in
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillBtn("√", { append("√(") }, Modifier.weight(1f).height(44.dp))
                        PillBtn("π", { append("3.14159") }, Modifier.weight(1f).height(44.dp))
                        PillBtn("^", { append("^") }, Modifier.weight(1f).height(44.dp))
                        PillBtn("!", { append("!") }, Modifier.weight(1f).height(44.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillBtn(if (isDegMode) "Deg" else "Rad", { isDegMode = !isDegMode }, Modifier.weight(1f).height(44.dp))
                        PillBtn("sin", { append("sin(") }, Modifier.weight(1f).height(44.dp))
                        PillBtn("cos", { append("cos(") }, Modifier.weight(1f).height(44.dp))
                        PillBtn("tan", { append("tan(") }, Modifier.weight(1f).height(44.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillBtn("Inv", { isInvMode = !isInvMode }, Modifier.weight(1f).height(44.dp), if (isInvMode) Primary50 else T30)
                        PillBtn("e", { append("2.718") }, Modifier.weight(1f).height(44.dp))
                        PillBtn("ln", { append("ln(") }, Modifier.weight(1f).height(44.dp))
                        PillBtn("log", { append("log(") }, Modifier.weight(1f).height(44.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            // Main Keypad - Anchored at bottom, shape transforms based on expanded state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // When expanded: buttons are pills (wider than tall) 
                // When collapsed: buttons are circles (1:1 ratio)
                val buttonHeight = if (expanded) 52.dp else null // Fixed height when expanded, aspectRatio when collapsed
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalcBtn("AC", Primary50, Color.White, { clear() }, Modifier.weight(1f), buttonShape, buttonHeight, true)
                    CalcBtn("( )", T30, OnSurfaceHigh, { val o = expression.count { it == '(' }; val c = expression.count { it == ')' }; append(if (o > c) ")" else "(") }, Modifier.weight(1f), buttonShape, buttonHeight, true)
                    CalcBtn("%", T30, OnSurfaceHigh, { append("%") }, Modifier.weight(1f), buttonShape, buttonHeight, true)
                    CalcBtn("÷", T30, OnSurfaceHigh, { appendOp("÷") }, Modifier.weight(1f), buttonShape, buttonHeight, true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalcBtn("7", T25, OnSurfaceHigh, { append("7") }, Modifier.weight(1f), buttonShape, buttonHeight, false)
                    CalcBtn("8", T25, OnSurfaceHigh, { append("8") }, Modifier.weight(1f), buttonShape, buttonHeight, false)
                    CalcBtn("9", T25, OnSurfaceHigh, { append("9") }, Modifier.weight(1f), buttonShape, buttonHeight, false)
                    CalcBtn("×", T30, OnSurfaceHigh, { appendOp("×") }, Modifier.weight(1f), buttonShape, buttonHeight, true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalcBtn("4", T25, OnSurfaceHigh, { append("4") }, Modifier.weight(1f), buttonShape, buttonHeight, false)
                    CalcBtn("5", T25, OnSurfaceHigh, { append("5") }, Modifier.weight(1f), buttonShape, buttonHeight, false)
                    CalcBtn("6", T25, OnSurfaceHigh, { append("6") }, Modifier.weight(1f), buttonShape, buttonHeight, false)
                    CalcBtn("−", T30, OnSurfaceHigh, { appendOp("-") }, Modifier.weight(1f), buttonShape, buttonHeight, true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalcBtn("1", T25, OnSurfaceHigh, { append("1") }, Modifier.weight(1f), buttonShape, buttonHeight, false)
                    CalcBtn("2", T25, OnSurfaceHigh, { append("2") }, Modifier.weight(1f), buttonShape, buttonHeight, false)
                    CalcBtn("3", T25, OnSurfaceHigh, { append("3") }, Modifier.weight(1f), buttonShape, buttonHeight, false)
                    CalcBtn("+", T30, OnSurfaceHigh, { appendOp("+") }, Modifier.weight(1f), buttonShape, buttonHeight, true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalcBtn("0", T25, OnSurfaceHigh, { append("0") }, Modifier.weight(1f), buttonShape, buttonHeight, false)
                    CalcBtn(".", T25, OnSurfaceHigh, { append(".") }, Modifier.weight(1f), buttonShape, buttonHeight, false)
                    // Backspace button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (buttonHeight != null) Modifier.height(buttonHeight) else Modifier.aspectRatio(1f))
                            .clip(buttonShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = { backspace() }
                            ),
                        shape = buttonShape, color = T25
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Outlined.Backspace, null, tint = OnSurfaceMedium, modifier = Modifier.size(24.dp))
                        }
                    }
                    CalcBtn("=", Tertiary80, Color.Black, { equals() }, Modifier.weight(1f), buttonShape, buttonHeight, true)
                }
            }
        }
    }
}

// Scientific button - Always pill/stadium shape
@Composable
private fun PillBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, bgColor: Color = T30) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(50)).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = onClick
        ),
        shape = RoundedCornerShape(50), color = bgColor
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = OnSurfaceHigh, fontFamily = Roboto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// Main keypad button - Shape changes based on expanded state
@Composable
private fun CalcBtn(
    text: String,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    fixedHeight: androidx.compose.ui.unit.Dp? = null,
    isOperator: Boolean = false
) {
    Surface(
        modifier = modifier
            .then(if (fixedHeight != null) Modifier.height(fixedHeight) else Modifier.aspectRatio(1f))
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            ),
        shape = shape, color = bgColor
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text, 
                color = textColor,
                fontFamily = Roboto,
                fontSize = when { 
                    text.length > 2 -> 20.sp
                    text in listOf("÷", "×", "−", "+", "=") -> 28.sp
                    else -> 26.sp 
                },
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
