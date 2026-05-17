package app.hida.vault.nativemodule

import android.app.Activity
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

private data class HistoryEntry(val expr: String, val result: String)
private const val MAX_HISTORY = 100

private data class CalcColors(
    val paperBg: Color,
    val displayBg: Color,
    val digitBg: Color,
    val functionBg: Color,
    /** Right-column operator / equals tiles (÷ × − + =); light stays ink-on-paper contrast. */
    val operatorBg: Color,
    val operatorFg: Color,
    val ink: Color,
    val mutedInk: Color,
    val danger: Color,
    val scrim: Color,
)

private val LightCalcColors = CalcColors(
    paperBg = Color(0xFFF5F1EA),
    displayBg = Color(0xFFEBE5DA),
    digitBg = Color.White,
    functionBg = Color(0xFFD9D2C4),
    operatorBg = Color(0xFF16140F),
    operatorFg = Color(0xFFF5F1EA),
    ink = Color(0xFF16140F),
    mutedInk = Color(0xFF6B6557),
    danger = Color(0xFFB3261E),
    scrim = Color(0x5916140F),
)

// Dark palette mirrors the LIGHT hierarchy: digits are the most elevated/brightest
// surface (primary tap target), function keys recede a step, display sits between.
// Previous palette inverted that — function was brighter than digits, which made the
// keypad read as "blank tiles with dim digits" instead of a calculator.
private val DarkCalcColors = CalcColors(
    paperBg = Color(0xFF0B0A09),
    displayBg = Color(0xFF1A1816),
    digitBg = Color(0xFF26221C),
    functionBg = Color(0xFF15130F),
    // Neo dark inactive tab plate (RN darkPalette.surface #1d1913): elevated charcoal vs cream-bright Ink tiles.
    operatorBg = Color(0xFF1D1913),
    operatorFg = Color(0xFFF2EEE4),
    ink = Color(0xFFF2EEE4),
    mutedInk = Color(0xFF8A857A),
    danger = Color(0xFFFF7A6E),
    scrim = Color(0xCC000000),
)

private val LocalCalcColors = staticCompositionLocalOf { LightCalcColors }

private val PaperBg: Color
    @Composable @ReadOnlyComposable get() = LocalCalcColors.current.paperBg
private val DisplayBg: Color
    @Composable @ReadOnlyComposable get() = LocalCalcColors.current.displayBg
private val DigitBg: Color
    @Composable @ReadOnlyComposable get() = LocalCalcColors.current.digitBg
private val FunctionBg: Color
    @Composable @ReadOnlyComposable get() = LocalCalcColors.current.functionBg
private val OperatorBg: Color
    @Composable @ReadOnlyComposable get() = LocalCalcColors.current.operatorBg
private val OperatorFg: Color
    @Composable @ReadOnlyComposable get() = LocalCalcColors.current.operatorFg
private val Ink: Color
    @Composable @ReadOnlyComposable get() = LocalCalcColors.current.ink
private val MutedInk: Color
    @Composable @ReadOnlyComposable get() = LocalCalcColors.current.mutedInk
private val Danger: Color
    @Composable @ReadOnlyComposable get() = LocalCalcColors.current.danger
private val Scrim: Color
    @Composable @ReadOnlyComposable get() = LocalCalcColors.current.scrim

private val DisplayShape = RoundedCornerShape(14.dp)
private val SquareShape = RoundedCornerShape(0.dp)
private val DisplayFont = FontFamily(Font(R.font.bricolage_grotesque, FontWeight.Bold))
private val ButtonFont = FontFamily(Font(R.font.space_grotesk, FontWeight.Bold))
private val MonoFont = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

private data class CalcLayoutMetrics(
    val contentWidth: Dp,
    val horizontalPadding: Dp,
    val topBarTopPadding: Dp,
    val topBarBottomPadding: Dp,
    val iconOuterSize: Dp,
    val iconInnerSize: Dp,
    val displayHeight: Dp,
    val displayHorizontalPadding: Dp,
    val displayVerticalPadding: Dp,
    val displayValueSize: Dp,
    val displayLineHeight: Dp,
    val historyLineSize: Dp,
    val gridGap: Dp,
    val keyHeight: Dp,
    val bottomPadding: Dp,
    val buttonTextSize: Dp,
    val equalsTextSize: Dp,
    val shadow: Dp,
    val menuTop: Dp,
    val menuWidth: Dp,
) {
    val topBarBlockHeight: Dp = topBarTopPadding + iconOuterSize + topBarBottomPadding
    val gridHeight: Dp = keyHeight * 5f + gridGap * 4f
}

private fun calcLayoutMetrics(maxWidth: Dp, maxHeight: Dp): CalcLayoutMetrics {
    // Buckets — used only to tweak paddings and font ranges, not to clamp the
    // grid: keys are sized purely from the actual available height/width so the
    // calculator fills any device (compact phone → tablet → foldable inner).
    val compactWidth = maxWidth < 360.dp
    val shortHeight = maxHeight < 640.dp
    val tabletWidth = maxWidth >= 600.dp

    val horizontalPadding = when {
        compactWidth -> 12.dp
        tabletWidth -> 32.dp
        else -> 16.dp
    }
    val availableWidth = (maxWidth - horizontalPadding * 2f).coerceAtLeast(292.dp)
    // Cap width so a foldable/tablet doesn't end up with ridiculously wide
    // keys; the calculator stays a comfortable hand-size.
    val contentWidth = availableWidth.coerceAtMost(if (tabletWidth) 520.dp else 460.dp)
    val gridGap = if (compactWidth || shortHeight) 8.dp else 10.dp

    val iconOuterSize = if (compactWidth || shortHeight) 43.dp else 47.dp
    val topBarTopPadding = if (shortHeight) 6.dp else 10.dp
    val topBarBottomPadding = if (shortHeight) 6.dp else 10.dp
    val bottomPadding = if (shortHeight) 10.dp else 16.dp

    // Total flexible vertical area under the top bar (the Box(weight=1f)).
    val topBarBlockHeight = topBarTopPadding + iconOuterSize + topBarBottomPadding
    val available = (maxHeight - topBarBlockHeight).coerceAtLeast(360.dp)

    // Display takes ~17% of the flexible area, with sensible floor/ceiling for
    // tiny / huge screens. Scaling means it grows on tall phones instead of
    // staying a fixed band that leaves dead space.
    val displayMin = if (shortHeight) 84f else 108f
    val displayMax = if (shortHeight) 118f else 200f
    val displayHeight = (available.value * 0.17f).coerceIn(displayMin, displayMax).dp

    // Everything else goes to the 5 keypad rows so the screen is exactly
    // filled (no top or bottom dead band on big or small phones).
    // Layout = displayHeight + gridGap (display→keypad) + 4*gridGap (between rows)
    //          + 5*keyHeight + bottomPadding == available
    val keysAvailable = available - bottomPadding - displayHeight - gridGap * 5f
    // Cap key height so a tall narrow phone doesn't get absurdly tall buttons.
    val keyHeight = (keysAvailable.value / 5f).coerceIn(48f, 132f).dp

    // Type sizes scale with the elements that contain them so big screens get
    // proportionally bigger numerals and labels.
    val displayValueSize = (displayHeight.value * 0.46f).coerceIn(38f, 64f).dp
    val displayLineHeight = (displayValueSize.value + 4f).dp
    val historyLineSize = if (compactWidth) 12.dp else 13.dp
    val displayHorizontalPadding = if (compactWidth) 18.dp else 22.dp
    val displayVerticalPadding = if (shortHeight) 12.dp else 16.dp

    val buttonTextSize = (keyHeight.value * 0.26f).coerceIn(18f, 30f).dp
    val equalsTextSize = (keyHeight.value * 0.30f).coerceIn(20f, 34f).dp

    return CalcLayoutMetrics(
        contentWidth = contentWidth,
        horizontalPadding = horizontalPadding,
        topBarTopPadding = topBarTopPadding,
        topBarBottomPadding = topBarBottomPadding,
        iconOuterSize = iconOuterSize,
        iconInnerSize = 44.dp.coerceAtMost(iconOuterSize),
        displayHeight = displayHeight,
        displayHorizontalPadding = displayHorizontalPadding,
        displayVerticalPadding = displayVerticalPadding,
        displayValueSize = displayValueSize,
        displayLineHeight = displayLineHeight,
        historyLineSize = historyLineSize,
        gridGap = gridGap,
        keyHeight = keyHeight,
        bottomPadding = bottomPadding,
        buttonTextSize = buttonTextSize,
        equalsTextSize = equalsTextSize,
        shadow = if (compactWidth || shortHeight) 3.dp else 4.dp,
        menuTop = topBarTopPadding + iconOuterSize + topBarBottomPadding + 4.dp,
        menuWidth = contentWidth.coerceAtMost(214.dp),
    )
}

private fun haptic(view: android.view.View, c: Int) {
    val safe = when (c) {
        HapticFeedbackConstants.CLOCK_TICK,
        HapticFeedbackConstants.CONFIRM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c
            else HapticFeedbackConstants.KEYBOARD_TAP
        }
        else -> c
    }
    runCatching { view.performHapticFeedback(safe) }
}

@Composable
fun CalculatorScreen(
    prefs: PreferencesManager,
    onUnlock: (mode: String) -> Unit,
    onRequestBiometric: () -> Unit,
    biometricAvailable: Boolean = false,
    /** Light/dark from RN ThemeProvider — survives view detach without stale remember(). */
    resolvedAppearance: String,
) {
    val isDark = resolvedAppearance == "dark"
    val calcColors = if (isDark) DarkCalcColors else LightCalcColors

    CompositionLocalProvider(LocalCalcColors provides calcColors) {
        CalculatorScreenContent(
            prefs = prefs,
            onUnlock = onUnlock,
            onRequestBiometric = onRequestBiometric,
            biometricAvailable = biometricAvailable,
            isDark = isDark,
        )
    }
}

@Composable
private fun CalculatorScreenContent(
    prefs: PreferencesManager,
    onUnlock: (mode: String) -> Unit,
    onRequestBiometric: () -> Unit,
    biometricAvailable: Boolean,
    isDark: Boolean,
) {
    val view = LocalView.current

    // Make status bar / nav bar icons follow the calculator theme so they
    // stay visible (light icons over dark calc bg, dark icons over cream bg).
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    var expression by remember { mutableStateOf("") }
    var prevExpr by remember { mutableStateOf("") }
    var prevResult by remember { mutableStateOf("") }

    var showMenu by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<HistoryEntry>() }

    var lockoutRemainingMs by remember { mutableLongStateOf(prefs.getRemainingLockoutTime()) }
    var showLockout by remember { mutableStateOf(false) }
    val unlockScope = rememberCoroutineScope()
    // Guard so a double-tap on `=` doesn't queue two PBKDF2 attempts (and the second
    // one race with navigation from the first). Stays true while a PIN-shape unlock
    // attempt is in flight on the worker thread.
    var inFlightUnlock by remember { mutableStateOf(false) }

    LaunchedEffect(showLockout) {
        while (showLockout) {
            val remaining = prefs.getRemainingLockoutTime()
            lockoutRemainingMs = remaining
            if (remaining <= 0L) {
                showLockout = false
                break
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    val liveResult = remember(expression) {
        if (expression.isEmpty() || expression == "Format error") ""
        else try {
            val expr = expression.replace("×", "*").replace("÷", "/")
            val result = CalcEngine.evalExpr(expr, true)
            if (result.isNaN() || result.isInfinite()) ""
            else {
                val formatted = CalcEngine.formatWithCommas(CalcEngine.formatResult(result))
                if (formatted == expression) "" else formatted
            }
        } catch (_: Exception) {
            ""
        }
    }

    val displayText = CalcEngine.formatExpression(expression)
    val historyLine = when {
        liveResult.isNotEmpty() -> "= $liveResult"
        prevExpr.isNotEmpty() -> "${CalcEngine.formatExpression(prevExpr)} = $prevResult"
        else -> ""
    }

    fun append(v: String) {
        showHistory = false
        haptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
        expression = if (expression == "Format error") v else expression + v
    }

    fun appendOp(op: String) {
        showHistory = false
        haptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
        val last = expression.lastOrNull()
        expression = when {
            expression.isEmpty() && op == "-" -> "-"
            expression == "Format error" -> "0"
            last in listOf('+', '-', '×', '÷') -> expression.dropLast(1) + op
            else -> expression + op
        }
    }

    fun clear() {
        showHistory = false
        haptic(view, HapticFeedbackConstants.LONG_PRESS)
        expression = ""
        prevExpr = ""
        prevResult = ""
    }

    fun backspace() {
        showHistory = false
        haptic(view, HapticFeedbackConstants.CLOCK_TICK)
        expression = if (expression.length <= 1) "" else expression.dropLast(1)
    }

    /** Synchronous math eval; updates expression/history/prevExpr in place. */
    fun runMathEval(input: String, isPinAttempt: Boolean) {
        try {
            val expr = input.replace("×", "*").replace("÷", "/")
            val result = CalcEngine.evalExpr(expr, true)
            if (result.isNaN()) {
                expression = "Format error"
            } else {
                val formatted = CalcEngine.formatResult(result)
                val display = CalcEngine.formatWithCommas(formatted)
                if (!isPinAttempt) {
                    history.add(HistoryEntry(input, display))
                    val overflow = history.size - MAX_HISTORY
                    if (overflow > 0) repeat(overflow) { history.removeAt(0) }
                }
                prevExpr = input
                prevResult = display
                expression = formatted
            }
        } catch (_: Exception) {
            expression = "Format error"
        }
    }

    fun equals() {
        showHistory = false
        haptic(view, HapticFeedbackConstants.CONFIRM)
        // Ignore taps while a previous PIN-shape attempt is still running PBKDF2 —
        // a second `=` press would either queue another 120k-iter KDF (perceived
        // as worse lag) or race the first attempt's navigation.
        if (inFlightUnlock) return
        if (prefs.isLockedOut()) {
            lockoutRemainingMs = prefs.getRemainingLockoutTime()
            showLockout = true
            return
        }

        val fakePin = prefs.getFakePin()
        val crypto = CryptoManager.getInstance(view.context)
        val validPinShape = expression.length in 4..10 && expression.all { it.isDigit() }
        val attempt = expression

        if (validPinShape && crypto.isVaultInitialized()) {
            // PIN-shape attempt against a real vault → run PBKDF2 OFF the UI thread.
            // The Compose thread keeps recomposing (button press animation, status
            // bar updates), so the user doesn't see a freeze — just the natural
            // ~150ms gap before navigation. Decoy and timing-equality logic stay
            // inside the coroutine so all paths take the same wall-clock time.
            inFlightUnlock = true
            unlockScope.launch {
                val realUnlocked = withContext(Dispatchers.Default) {
                    crypto.unlockWithPin(attempt)
                }
                inFlightUnlock = false
                if (realUnlocked) {
                    prefs.clearFailedAttempts()
                    prefs.clearSession()
                    onUnlock("real")
                    return@launch
                }
                if (!fakePin.isNullOrEmpty() && attempt == fakePin) {
                    crypto.lockVault()
                    onUnlock("fake")
                    return@launch
                }
                val lockoutDuration = prefs.recordFailedAttempt()
                if (lockoutDuration > 0) {
                    lockoutRemainingMs = lockoutDuration
                    showLockout = true
                }
                runMathEval(attempt, isPinAttempt = true)
            }
            return
        }

        // No vault yet (welcome flow before setupVault) or expression isn't PIN-shape.
        // Decoy can still match (rare edge case) and math always runs; PBKDF2 is not
        // needed here, so the path stays synchronous and instant.
        if (validPinShape && !fakePin.isNullOrEmpty() && attempt == fakePin) {
            crypto.lockVault()
            onUnlock("fake")
            return
        }
        if (validPinShape) {
            // Burn an equivalent KDF off-thread for timing parity with future attempts
            // once a vault exists; the result is discarded.
            unlockScope.launch(Dispatchers.Default) { crypto.runDummyKdf() }
        }
        runMathEval(attempt, isPinAttempt = validPinShape)
    }

    fun closeMenuWithHaptic(feedback: Int = HapticFeedbackConstants.KEYBOARD_TAP) {
        haptic(view, feedback)
        showMenu = false
    }

    if (showLockout) {
        AlertDialog(
            onDismissRequest = { showLockout = false },
            title = { Text("Too Many Attempts") },
            text = {
                val seconds = ((lockoutRemainingMs + 999) / 1000).toInt().coerceAtLeast(0)
                Text("Please wait ${seconds}s before trying again")
            },
            confirmButton = {
                TextButton(onClick = { showLockout = false }) {
                    Text("OK")
                }
            }
        )
    }

    val scheme = if (isDark) {
        darkColorScheme(
            surface = PaperBg,
            onSurface = Ink,
            surfaceVariant = DisplayBg,
            onSurfaceVariant = MutedInk,
            primary = Ink,
            onPrimary = PaperBg,
        )
    } else {
        lightColorScheme(
            surface = PaperBg,
            onSurface = Ink,
            surfaceVariant = DisplayBg,
            onSurfaceVariant = MutedInk,
            primary = Ink,
            onPrimary = PaperBg,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(PaperBg)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical))
        ) {
            val metrics = calcLayoutMetrics(maxWidth, maxHeight)

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TopBar(
                        metrics = metrics,
                        onMenu = {
                            haptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                            showMenu = !showMenu
                        },
                        onHistory = {
                            haptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                            showHistory = true
                        }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .width(metrics.contentWidth)
                                .padding(bottom = metrics.bottomPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            NeoDisplay(
                                historyLine = historyLine,
                                value = if (displayText.isEmpty()) "0" else displayText,
                                isError = displayText == "Format error",
                                metrics = metrics,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(metrics.displayHeight)
                            )
                            Spacer(Modifier.height(metrics.gridGap))
                            Keypad(
                                metrics = metrics,
                                onClear = { clear() },
                                onBackspace = { backspace() },
                                onAppend = ::append,
                                onAppendOp = ::appendOp,
                                onEquals = { equals() },
                            )
                        }
                    }
                }

                if (showMenu) {
                    MenuOverlay(
                        metrics = metrics,
                        biometricAvailable = biometricAvailable,
                        onDismiss = { showMenu = false },
                        onUnlock = {
                            showMenu = false
                            haptic(view, HapticFeedbackConstants.CONFIRM)
                            onRequestBiometric()
                        },
                        onDecoy = { closeMenuWithHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                        modifier = Modifier.zIndex(5f)
                    )
                }

                AnimatedVisibility(
                    visible = showHistory,
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(100)),
                    modifier = Modifier.zIndex(10f)
                ) {
                    HistoryPanel(
                        metrics = metrics,
                        history = history.reversed(),
                        onBack = {
                            haptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                            showHistory = false
                        },
                        onClear = {
                            haptic(view, HapticFeedbackConstants.CLOCK_TICK)
                            history.clear()
                        },
                        onSelect = { entry ->
                            haptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                            expression = entry.result.replace(",", "")
                            showHistory = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    metrics: CalcLayoutMetrics,
    onMenu: () -> Unit,
    onHistory: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(metrics.contentWidth)
            .padding(top = metrics.topBarTopPadding, bottom = metrics.topBarBottomPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeoIconButton(metrics = metrics, onClick = onMenu, contentDescription = "Menu") {
            Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
        }
        NeoIconButton(metrics = metrics, onClick = onHistory, contentDescription = "History") {
            Icon(Icons.Outlined.History, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Keypad(
    metrics: CalcLayoutMetrics,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onAppend: (String) -> Unit,
    onAppendOp: (String) -> Unit,
    onEquals: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.gridHeight),
        verticalArrangement = Arrangement.spacedBy(metrics.gridGap)
    ) {
        KeypadRow(metrics) {
            CalcButton("AC", onClear, Modifier.weight(1f), metrics = metrics, bg = FunctionBg)
            BackspaceButton(onBackspace, Modifier.weight(1f), metrics = metrics, bg = FunctionBg)
            CalcButton("%", { onAppendOp("%") }, Modifier.weight(1f), metrics = metrics, bg = FunctionBg)
            CalcButton("÷", { onAppendOp("÷") }, Modifier.weight(1f), metrics = metrics, bg = OperatorBg, fg = OperatorFg)
        }
        KeypadRow(metrics) {
            CalcButton("7", { onAppend("7") }, Modifier.weight(1f), metrics = metrics)
            CalcButton("8", { onAppend("8") }, Modifier.weight(1f), metrics = metrics)
            CalcButton("9", { onAppend("9") }, Modifier.weight(1f), metrics = metrics)
            CalcButton("×", { onAppendOp("×") }, Modifier.weight(1f), metrics = metrics, bg = OperatorBg, fg = OperatorFg)
        }
        KeypadRow(metrics) {
            CalcButton("4", { onAppend("4") }, Modifier.weight(1f), metrics = metrics)
            CalcButton("5", { onAppend("5") }, Modifier.weight(1f), metrics = metrics)
            CalcButton("6", { onAppend("6") }, Modifier.weight(1f), metrics = metrics)
            CalcButton("−", { onAppendOp("-") }, Modifier.weight(1f), metrics = metrics, bg = OperatorBg, fg = OperatorFg)
        }
        KeypadRow(metrics) {
            CalcButton("1", { onAppend("1") }, Modifier.weight(1f), metrics = metrics)
            CalcButton("2", { onAppend("2") }, Modifier.weight(1f), metrics = metrics)
            CalcButton("3", { onAppend("3") }, Modifier.weight(1f), metrics = metrics)
            CalcButton("+", { onAppendOp("+") }, Modifier.weight(1f), metrics = metrics, bg = OperatorBg, fg = OperatorFg)
        }
        KeypadRow(metrics) {
            CalcButton("0", { onAppend("0") }, Modifier.weight(2f), metrics = metrics)
            CalcButton(".", { onAppend(".") }, Modifier.weight(1f), metrics = metrics)
            CalcButton("=", onEquals, Modifier.weight(1f), metrics = metrics, bg = OperatorBg, fg = OperatorFg)
        }
    }
}

@Composable
private fun KeypadRow(
    metrics: CalcLayoutMetrics,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.keyHeight),
        horizontalArrangement = Arrangement.spacedBy(metrics.gridGap),
        content = content
    )
}

@Composable
private fun NeoDisplay(
    historyLine: String,
    value: String,
    isError: Boolean,
    metrics: CalcLayoutMetrics,
    modifier: Modifier = Modifier,
) {
    HardSurface(
        modifier = modifier,
        shape = DisplayShape,
        bg = DisplayBg,
        shadow = metrics.shadow,
        pressable = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = metrics.displayHorizontalPadding, vertical = metrics.displayVerticalPadding),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = historyLine,
                color = Ink.copy(alpha = 0.45f),
                fontFamily = MonoFont,
                fontSize = metrics.historyLineSize.value.sp,
                lineHeight = (metrics.historyLineSize.value + 3f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                color = if (isError) Danger else Ink,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = when {
                    value.length > 16 -> (metrics.displayValueSize.value - 18f).coerceAtLeast(30f).sp
                    value.length > 12 -> (metrics.displayValueSize.value - 12f).sp
                    value.length > 8 -> (metrics.displayValueSize.value - 6f).sp
                    else -> metrics.displayValueSize.value.sp
                },
                lineHeight = metrics.displayLineHeight.value.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CalcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: CalcLayoutMetrics,
    bg: Color = DigitBg,
    fg: Color = Ink,
    noShadow: Boolean = false,
) {
    HardSurface(
        modifier = modifier.fillMaxHeight(),
        shape = SquareShape,
        bg = bg,
        shadow = if (noShadow) 0.dp else metrics.shadow,
        onClick = onClick
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = fg,
                fontFamily = ButtonFont,
                fontWeight = FontWeight.Bold,
                fontSize = if (text == "=") metrics.equalsTextSize.value.sp else metrics.buttonTextSize.value.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BackspaceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: CalcLayoutMetrics,
    bg: Color = FunctionBg,
) {
    HardSurface(
        modifier = modifier.fillMaxHeight(),
        shape = SquareShape,
        bg = bg,
        shadow = metrics.shadow,
        onClick = onClick
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Backspace",
                tint = Ink,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun NeoIconButton(
    metrics: CalcLayoutMetrics,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    HardSurface(
        modifier = Modifier.size(metrics.iconOuterSize),
        shape = SquareShape,
        bg = PaperBg,
        shadow = 3.dp,
        onClick = onClick
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(metrics.iconInnerSize), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

@Composable
private fun HardSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    bg: Color,
    shadow: Dp,
    shadowColor: Color = Ink,
    borderColor: Color = Ink,
    pressable: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val progress = pressAnim.value

    val foregroundOffset = (shadow.value * progress).dp
    val shadowAmt = (shadow.value * (1f - progress)).dp
    val faceScale = 1f - 0.02f * progress

    Box(modifier = modifier.padding(end = shadow, bottom = shadow)) {
        Surface(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowAmt, y = shadowAmt),
            color = shadowColor,
            shape = shape
        ) {}
        Surface(
            modifier = Modifier
                .matchParentSize()
                .offset(x = foregroundOffset, y = foregroundOffset)
                .scale(faceScale)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = source,
                            indication = null,
                            onClick = {
                                if (pressable) {
                                    scope.launch {
                                        pressAnim.animateTo(1f, tween(40))
                                        pressAnim.animateTo(0f, tween(120))
                                    }
                                }
                                onClick()
                            }
                        )
                    } else {
                        Modifier
                    }
                ),
            color = bg,
            shape = shape,
            border = BorderStroke(2.dp, borderColor),
        ) {
            content()
        }
    }
}

@Composable
private fun MenuOverlay(
    metrics: CalcLayoutMetrics,
    biometricAvailable: Boolean,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    onDecoy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        Surface(
            modifier = Modifier
                .padding(start = metrics.horizontalPadding, top = metrics.menuTop)
                .width(metrics.menuWidth)
                .offset(x = 4.dp, y = 4.dp),
            color = Ink,
            shape = SquareShape
        ) {}
        Surface(
            modifier = Modifier
                .padding(start = metrics.horizontalPadding, top = metrics.menuTop)
                .width(metrics.menuWidth),
            color = PaperBg,
            shape = SquareShape,
            border = BorderStroke(2.dp, Ink),
        ) {
            Column {
                if (biometricAvailable) {
                    MenuRow(
                        label = "Unlock",
                        icon = { Icon(Icons.Outlined.LockOpen, null, tint = Ink, modifier = Modifier.size(18.dp)) },
                        onClick = onUnlock
                    )
                    HorizontalDivider(color = Ink, thickness = 1.dp)
                }
                MenuRow(
                    label = "Scientific",
                    icon = { Icon(Icons.Outlined.Calculate, null, tint = Ink, modifier = Modifier.size(18.dp)) },
                    badge = "PRO",
                    onClick = onDecoy
                )
                HorizontalDivider(color = Ink, thickness = 1.dp)
                MenuRow(
                    label = "Settings",
                    icon = { Icon(Icons.Outlined.Settings, null, tint = Ink, modifier = Modifier.size(18.dp)) },
                    onClick = onDecoy
                )
                HorizontalDivider(color = Ink, thickness = 1.dp)
                MenuRow(
                    label = "About",
                    icon = { Icon(Icons.Outlined.Info, null, tint = Ink, modifier = Modifier.size(18.dp)) },
                    onClick = onDecoy
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(49.dp)
            .background(PaperBg)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Text(
            text = label,
            color = if (enabled) Ink else Ink.copy(alpha = 0.55f),
            fontFamily = ButtonFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Text(
                text = badge,
                color = Ink.copy(alpha = 0.5f),
                fontFamily = MonoFont,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun HistoryPanel(
    metrics: CalcLayoutMetrics,
    history: List<HistoryEntry>,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onSelect: (HistoryEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = metrics.horizontalPadding,
                    end = metrics.horizontalPadding,
                    top = metrics.topBarTopPadding,
                    bottom = metrics.topBarBottomPadding
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeoIconButton(metrics = metrics, onClick = onBack, contentDescription = "Back") {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "History",
                color = Ink,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 0.72.sp,
            )
            NeoIconButton(metrics = metrics, onClick = onClear, contentDescription = "Clear history") {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(color = Ink, thickness = 2.dp)

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No calculations yet",
                    color = MutedInk,
                    fontFamily = MonoFont,
                    fontSize = 12.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = metrics.horizontalPadding,
                    end = metrics.horizontalPadding,
                    top = 12.dp,
                    bottom = metrics.bottomPadding + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history) { entry ->
                    HistoryRow(entry = entry, onClick = { onSelect(entry) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
) {
    HardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
        shape = SquareShape,
        bg = DigitBg,
        shadow = 3.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = CalcEngine.formatExpression(entry.expr),
                color = MutedInk,
                fontFamily = MonoFont,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "= ${entry.result}",
                color = Ink,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
