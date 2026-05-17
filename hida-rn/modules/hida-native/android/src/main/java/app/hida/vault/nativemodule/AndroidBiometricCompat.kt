package app.hida.vault.nativemodule

import android.content.Context
import androidx.biometric.BiometricManager

/**
 * Uses AndroidX BiometricManager so we match what the system considers usable,
 * which is more reliable than JS [hasHardwareAsync] alone on some OEMs.
 */
object AndroidBiometricCompat {
    fun canAuthenticate(context: Context): Boolean {
        val bm = BiometricManager.from(context)
        val mask =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        return bm.canAuthenticate(mask) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
