package app.hida.vault.nativemodule

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView

// Own our LifecycleOwner / SavedStateRegistryOwner so the embedded ComposeView
// always has a valid lifecycle, regardless of whether the RN/Fabric parent
// chain propagates one. Without this, the second-and-later mount (welcome →
// /calculator, or gallery lock → /calculator) rendered blank because the
// ComposeView's recomposer had no lifecycle to attach to.
class CalculatorView(
    context: Context,
    appContext: AppContext
) : ExpoView(context, appContext), LifecycleOwner, SavedStateRegistryOwner {

    val onUnlock by EventDispatcher()
    val onRequestBiometric by EventDispatcher()

    private val biometricState = mutableStateOf(false)
    private val prefs = PreferencesManager(context)
    private val themeResolvedState = mutableStateOf(computeResolvedTheme(prefs, context))

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private var lastLayoutWidth = 0
    private var lastLayoutHeight = 0

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        // Fabric may attach our parent before the child ComposeView, so re-measure
        // the child whenever it actually attaches using the parent's last layout size.
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                measureAndLayoutComposeChild(lastLayoutWidth, lastLayoutHeight)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }

    init {
        try {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED

            setViewTreeLifecycleOwner(this)
            setViewTreeSavedStateRegistryOwner(this)
            composeView.setViewTreeLifecycleOwner(this)
            composeView.setViewTreeSavedStateRegistryOwner(this)

            composeView.setContent {
                CalculatorScreen(
                    prefs = prefs,
                    biometricAvailable = biometricState.value,
                    resolvedAppearance = themeResolvedState.value,
                    onUnlock = { mode ->
                        try { onUnlock(mapOf("mode" to mode)) }
                        catch (t: Throwable) { Log.e(TAG, "onUnlock dispatch failed", t) }
                    },
                    onRequestBiometric = {
                        try { onRequestBiometric(mapOf<String, Any>()) }
                        catch (t: Throwable) { Log.e(TAG, "onRequestBiometric dispatch failed", t) }
                    },
                )
            }

            addView(
                composeView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "init failed", t)
            throw t
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            // Re-assert in case a parent clobbered either tree owner between init and attach.
            setViewTreeLifecycleOwner(this)
            setViewTreeSavedStateRegistryOwner(this)
            composeView.setViewTreeLifecycleOwner(this)
            composeView.setViewTreeSavedStateRegistryOwner(this)

            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            measureAndLayoutComposeChild(width, height)
            requestLayout()
            composeView.requestLayout()
        } catch (t: Throwable) {
            Log.e(TAG, "onAttachedToWindow failed", t)
        }
    }

    override fun onDetachedFromWindow() {
        try {
            // Step back to CREATED (not DESTROYED) so react-native-screens can
            // detach/re-attach during transitions without disposing the composition.
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        } catch (t: Throwable) {
            Log.e(TAG, "onDetachedFromWindow lifecycle transition failed", t)
        }
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = View.MeasureSpec.getSize(widthMeasureSpec)
        val h = View.MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        // Fabric doesn't lay out our native composeView child — measure it ourselves
        // or it stays 0x0 and the Compose UI never draws (the cold-start bail-out
        // doesn't happen on the second-and-later mount via router.replace).
        // Only measure the child when it's actually attached, otherwise Compose's
        // WindowRecomposer lookup throws.
        if (composeView.isAttachedToWindow) {
            measureAndLayoutComposeChild(w, h)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        lastLayoutWidth = w
        lastLayoutHeight = h
        composeView.layout(0, 0, w, h)
    }

    private fun measureAndLayoutComposeChild(w: Int, h: Int) {
        if (w <= 0 || h <= 0 || !composeView.isAttachedToWindow) return
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, w, h)
    }

    fun setBiometricAvailable(available: Boolean) {
        post {
            if (biometricState.value != available) {
                biometricState.value = available
            }
        }
    }

    fun setThemeResolved(mode: String) {
        post {
            val normalized = if (mode == "dark") "dark" else "light"
            if (themeResolvedState.value != normalized) {
                themeResolvedState.value = normalized
            }
        }
    }

    companion object {
        private const val TAG = "HidaCalcView"

        private fun computeResolvedTheme(prefs: PreferencesManager, context: Context): String {
            return when (prefs.getThemeMode()) {
                "dark" -> "dark"
                "light" -> "light"
                else -> {
                    val night =
                        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (night == Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
                }
            }
        }
    }
}
