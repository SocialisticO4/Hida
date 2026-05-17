package app.hida.vault.nativemodule

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.Promise
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Vault biometric using [BiometricPrompt] (same stack as expo-local-authentication), with disguise
 * task branding and a reflective [BiometricPrompt.PromptInfo.Builder.setLogo] when the ART / OEM
 * allows it ([`SET_BIOMETRIC_DIALOG_ADVANCED`] blocks most apps — attempt is harmless).
 */
object VaultBiometricHelper {

    /**
     * Cryptographic vault unlock: gates the BiometricPrompt with a [CryptoObject] tied
     * to the biometric-required Keystore key. On success, the authorised cipher is
     * used to unwrap the ChaCha20 file key into the [CryptoManager] session.
     *
     * Result bundle:
     *   success: true                                                    → vault unlocked
     *   success: false, error: "not_enrolled"                            → biometric not set up for vault
     *   success: false, error: "key_invalidated"                         → wrap was invalidated (re-enrollment needed)
     *   success: false, error: "user_cancel" | other BiometricPrompt err → standard codes
     */
    fun authenticateAndUnlock(
        appContext: AppContext,
        cryptoManager: CryptoManager,
        prefs: PreferencesManager,
        promptMessage: String,
        promise: Promise,
    ) {
        val activity = appContext.throwingActivity
        val fragmentActivity = activity as? FragmentActivity
        if (fragmentActivity == null) {
            promise.resolve(failBundle("not_available", "BiometricPrompt requires FragmentActivity"))
            return
        }

        if (!cryptoManager.isBiometricUnlockEnrolled()) {
            promise.resolve(failBundle("not_enrolled", "Biometric unlock not enrolled"))
            return
        }
        val cipher = cryptoManager.biometricDecryptCipher()
        if (cipher == null) {
            // Keystore key is gone or IV missing — likely invalidated by new fingerprint enroll.
            cryptoManager.disableBiometricUnlock()
            promise.resolve(failBundle("key_invalidated", "Biometric wrap unavailable"))
            return
        }

        LauncherDisguiseBranding.apply(activity, prefs)

        val executor: Executor = Executors.newSingleThreadExecutor()
        var settled = false

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (settled) return
                settled = true
                val authCipher = result.cryptoObject?.cipher
                val ok = authCipher != null && cryptoManager.finishBiometricUnlock(authCipher)
                if (ok) {
                    promise.resolve(Bundle().apply { putBoolean("success", true) })
                } else {
                    cryptoManager.disableBiometricUnlock()
                    promise.resolve(failBundle("key_invalidated", "Biometric unwrap failed"))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (settled) return
                settled = true
                promise.resolve(
                    Bundle().apply {
                        putBoolean("success", false)
                        putString("error", mapAuthError(errorCode))
                        putString("warning", errString.toString())
                    }
                )
            }
        }

        val prompt = BiometricPrompt(fragmentActivity, executor, callback)
        // CryptoObject-backed prompts must use BIOMETRIC_STRONG (Class 3); device credential
        // is not allowed when CryptoObject is present.
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(LauncherDisguiseBranding.displayNameForAlias(prefs.getIconAlias()))
            .setSubtitle(promptMessage)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(true)

        attachDisguiseLogoIfPossible(builder, fragmentActivity, prefs)

        try {
            prompt.authenticate(builder.build(), BiometricPrompt.CryptoObject(cipher))
        } catch (_: Throwable) {
            promise.resolve(failBundle("unable_to_process", "Biometric authenticate threw"))
        }
    }

    /**
     * Biometric-bound enrollment. Creates the Keystore key, builds a fresh
     * ENCRYPT-mode cipher, gates the BiometricPrompt with that cipher's
     * CryptoObject, and on success uses the now-authorised cipher to wrap the
     * in-memory ChaCha20 file key.
     *
     * This is the ONLY correct enrollment path: a plain "did user auth" prompt
     * (without CryptoObject) cannot authorise a key built with
     * `setUserAuthenticationRequired(true)`, so doFinal would throw
     * `UserNotAuthenticatedException` and silent enrollment failure was reported
     * to users as "Not available on this device."
     */
    fun authenticateAndEnroll(
        appContext: AppContext,
        cryptoManager: CryptoManager,
        prefs: PreferencesManager,
        promptMessage: String,
        promise: Promise,
    ) {
        val activity = appContext.throwingActivity
        val fragmentActivity = activity as? FragmentActivity
        if (fragmentActivity == null) {
            promise.resolve(failBundle("not_available", "BiometricPrompt requires FragmentActivity"))
            return
        }

        if (!cryptoManager.isVaultUnlocked()) {
            promise.resolve(failBundle("not_unlocked", "Vault must be unlocked before enrolling biometric"))
            return
        }

        val cipher = cryptoManager.biometricEnrollCipher()
        if (cipher == null) {
            promise.resolve(failBundle("not_available", "Could not create biometric Keystore key"))
            return
        }

        LauncherDisguiseBranding.apply(activity, prefs)

        val executor: Executor = Executors.newSingleThreadExecutor()
        var settled = false

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (settled) return
                settled = true
                val authCipher = result.cryptoObject?.cipher
                val ok = authCipher != null && cryptoManager.finishBiometricEnroll(authCipher)
                if (ok) {
                    promise.resolve(Bundle().apply { putBoolean("success", true) })
                } else {
                    cryptoManager.disableBiometricUnlock()
                    promise.resolve(failBundle("unable_to_process", "Biometric enroll wrap failed"))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (settled) return
                settled = true
                // Drop any partial Keystore state on error so a future enroll attempt starts clean.
                cryptoManager.disableBiometricUnlock()
                promise.resolve(
                    Bundle().apply {
                        putBoolean("success", false)
                        putString("error", mapAuthError(errorCode))
                        putString("warning", errString.toString())
                    }
                )
            }
        }

        val prompt = BiometricPrompt(fragmentActivity, executor, callback)
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(LauncherDisguiseBranding.displayNameForAlias(prefs.getIconAlias()))
            .setSubtitle(promptMessage)
            // CryptoObject-backed prompts must use BIOMETRIC_STRONG; device credential
            // is not allowed when CryptoObject is present.
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(true)

        attachDisguiseLogoIfPossible(builder, fragmentActivity, prefs)

        try {
            prompt.authenticate(builder.build(), BiometricPrompt.CryptoObject(cipher))
        } catch (_: Throwable) {
            cryptoManager.disableBiometricUnlock()
            promise.resolve(failBundle("unable_to_process", "Biometric authenticate threw"))
        }
    }

    /**
     * Plain "did the user pass biometric" gate. Used for non-cryptographic confirmations
     * (e.g. confirming the user is present before enabling biometric in Settings).
     */
    fun authenticateLikeExpoDefault(
        appContext: AppContext,
        prefs: PreferencesManager,
        promptMessage: String,
        promise: Promise,
    ) {
        val activity = appContext.throwingActivity
        val fragmentActivity = activity as? FragmentActivity
        if (fragmentActivity == null) {
            promise.resolve(failBundle("not_available", "BiometricPrompt requires FragmentActivity"))
            return
        }

        LauncherDisguiseBranding.apply(activity, prefs)

        val executor: Executor = Executors.newSingleThreadExecutor()
        var settled = false

        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (settled) return
                    settled = true
                    promise.resolve(Bundle().apply { putBoolean("success", true) })
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (settled) return
                    settled = true
                    promise.resolve(
                        Bundle().apply {
                            putBoolean("success", false)
                            putString("error", mapAuthError(errorCode))
                            putString("warning", errString.toString())
                        }
                    )
                }
            }

        val prompt = BiometricPrompt(fragmentActivity, executor, callback)
        val allowedAuthenticators =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val disguiseLabel = LauncherDisguiseBranding.displayNameForAlias(prefs.getIconAlias())
        val builder =
            BiometricPrompt.PromptInfo.Builder()
                // Title is what OEM sheets emphasize — match launcher label (e.g. Gmail, Chrome).
                .setTitle(disguiseLabel)
                .setSubtitle(promptMessage)
                .setAllowedAuthenticators(allowedAuthenticators)
                .setConfirmationRequired(true)

        attachDisguiseLogoIfPossible(builder, fragmentActivity, prefs)

        try {
            prompt.authenticate(builder.build())
        } catch (_: Throwable) {
            promise.resolve(failBundle("unable_to_process", "Biometric authenticate threw"))
        }
    }

    private fun attachDisguiseLogoIfPossible(
        builder: BiometricPrompt.PromptInfo.Builder,
        activity: Activity,
        prefs: PreferencesManager,
    ) {
        runCatching {
            val resId =
                LauncherDisguiseBranding.launcherIconResId(activity.resources, activity.packageName, prefs)
            if (resId == 0) return
            val drawable = ContextCompat.getDrawable(activity, resId) ?: return
            val logoPx = 288
            val bmp =
                Bitmap.createBitmap(logoPx, logoPx, Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, logoPx, logoPx)
                    drawable.draw(canvas)
                }
            val icon =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Icon.createWithAdaptiveBitmap(bmp)
                } else {
                    @Suppress("DEPRECATION")
                    Icon.createWithBitmap(bmp)
                }
            val m =
                BiometricPrompt.PromptInfo.Builder::class.java.methods.firstOrNull { method ->
                    method.name == "setLogo" && method.parameterTypes.size == 1
                } ?: return
            m.invoke(builder, icon)
        }
    }

    private fun mapAuthError(code: Int): String =
        when (code) {
            BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_USER_CANCELED,
            -> "user_cancel"

            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
            -> "not_available"

            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
            -> "lockout"

            BiometricPrompt.ERROR_NO_SPACE -> "no_space"
            BiometricPrompt.ERROR_TIMEOUT -> "timeout"
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> "unable_to_process"
            else -> "unknown"
        }

    private fun failBundle(code: String, warning: String): Bundle =
        Bundle().apply {
            putBoolean("success", false)
            putString("error", code)
            putString("warning", warning)
        }
}
