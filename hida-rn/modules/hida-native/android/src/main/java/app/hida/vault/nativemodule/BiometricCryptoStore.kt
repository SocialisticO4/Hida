package app.hida.vault.nativemodule

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages a biometric-required Android Keystore key that wraps the in-memory
 * ChaCha20 file key. Without successful BiometricPrompt, the wrap cannot be
 * inverted — even by the app process — because the Keystore enforces user-auth
 * on cipher use.
 *
 * Flow:
 *  enroll(chaChaKeyBytes)         — called once, while vault is unlocked, to enable biometric.
 *  decryptCipher()                — returns Cipher init'd in DECRYPT_MODE; pass to BiometricPrompt.
 *  unwrap(authorisedCipher)       — after BiometricPrompt success, recover ChaCha20 key bytes.
 *  disable()                      — delete Keystore entry + persisted blob.
 */
class BiometricCryptoStore {

    private val keyStore: KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun isEnrolled(prefs: PreferencesManager): Boolean =
        prefs.getBiometricWrappedKey() != null &&
            prefs.getBiometricWrappedIv() != null &&
            keyStore.containsAlias(KEY_ALIAS)

    /**
     * Build the cipher used for enrollment. Caller MUST pass this cipher into
     * [androidx.biometric.BiometricPrompt.CryptoObject] and only call [finishEnroll]
     * with the cipher returned in the success result. Calling `cipher.doFinal()`
     * directly here would throw `UserNotAuthenticatedException` because the key is
     * created with `setUserAuthenticationRequired(true)`.
     */
    fun enrollCipher(): Cipher? {
        return try {
            // Recreate the alias on every enroll so a fresh IV/key is used.
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
            val ksKey = createBiometricKey()
            Cipher.getInstance(TRANSFORMATION).also {
                it.init(Cipher.ENCRYPT_MODE, ksKey)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Use the now-authorised cipher (after BiometricPrompt success) to wrap the key. */
    fun finishEnroll(
        authorisedCipher: Cipher,
        chaChaKeyBytes: ByteArray,
        prefs: PreferencesManager,
    ): Boolean {
        return try {
            val iv = authorisedCipher.iv
            val wrapped = authorisedCipher.doFinal(chaChaKeyBytes)
            prefs.saveBiometricWrappedKey(
                Base64.encodeToString(wrapped, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP),
            )
            true
        } catch (_: Exception) {
            disable(prefs)
            false
        }
    }

    /** Caller passes this into [androidx.biometric.BiometricPrompt.CryptoObject]. */
    fun decryptCipher(prefs: PreferencesManager): Cipher? {
        return try {
            val ivStr = prefs.getBiometricWrappedIv() ?: return null
            val iv = Base64.decode(ivStr, Base64.NO_WRAP)
            val ksKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return null
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, ksKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
        } catch (_: Exception) {
            null
        }
    }

    /** After successful BiometricPrompt, the [authorisedCipher] is single-use authorised. */
    fun unwrap(authorisedCipher: Cipher, prefs: PreferencesManager): ByteArray? {
        return try {
            val wrappedStr = prefs.getBiometricWrappedKey() ?: return null
            val wrapped = Base64.decode(wrappedStr, Base64.NO_WRAP)
            authorisedCipher.doFinal(wrapped)
        } catch (_: Exception) {
            null
        }
    }

    fun disable(prefs: PreferencesManager) {
        try {
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
            // ignored — clearing prefs is the source of truth from the user's perspective
        }
        prefs.clearBiometricWrappedKey()
    }

    private fun createBiometricKey(): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // Invalidate the wrap when the user changes/adds a fingerprint, mirroring
            // best practice for biometric-bound keys.
            .setInvalidatedByBiometricEnrollment(true)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .also { it.init(builder.build()) }
            .generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "HidaVaultBiometricKey"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
