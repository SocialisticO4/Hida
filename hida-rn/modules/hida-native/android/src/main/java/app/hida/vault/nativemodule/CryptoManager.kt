package app.hida.vault.nativemodule

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoManager — PIN-bound chunked ChaCha20-Poly1305 encryption.
 *
 * The 32-byte ChaCha20 file key is wrapped under a PBKDF2-HMAC-SHA256 key using
 * AES-256/GCM. The PIN is first peppered via HMAC-SHA256 keyed by an Android
 * Keystore HMAC-SHA256 key (alias [PEPPER_KEY_ALIAS]) before being fed to PBKDF2
 * with 600k iterations. The pepper key cannot be exported from Keystore, so an
 * attacker with the encrypted prefs but no Keystore access cannot brute-force a
 * short PIN — the 10^6 (6-digit) search space alone is no longer enough.
 *
 * Vaults written by older builds (pre-pepper, 120k iterations, plain PIN) are
 * marked as "legacy" via [isVaultLegacyFormat]; the JS app prompts the user to
 * reset before unlocking. There is no in-place migration: legacy data is wiped.
 *
 * The unwrapped ChaCha20 key lives only in process memory while the vault session
 * is "unlocked"; calling [lockVault] zeroes it. App restart, session timeout, and
 * the lock button all clear it.
 *
 * File format on disk (unchanged):
 * [12-byte master nonce][chunk_0 ct + 16B tag][chunk_1 ct + 16B tag] ...
 */
class CryptoManager private constructor(context: Context) {

    private val prefs = PreferencesManager(context)
    private val biometricStore = BiometricCryptoStore()

    // The session key + the raw bytes that back it. Holding both lets [lockVault]
    // zero the underlying ByteArray; SecretKeySpec.encoded only returns a clone, so
    // without the raw reference the original heap bytes survive until GC.
    private val sessionLock = Any()
    @Volatile private var sessionChaChaKey: SecretKey? = null
    @Volatile private var sessionChaChaBytes: ByteArray? = null

    // ==================== Vault state ====================

    fun isVaultInitialized(): Boolean =
        prefs.getPinSalt() != null && prefs.getWrappedKeyV2() != null

    fun isVaultUnlocked(): Boolean = sessionChaChaKey != null

    /**
     * True if the on-disk vault was written by a pre-pepper build. The JS app
     * checks this on startup so it can warn the user that resetting is required
     * before they reach the calculator unlock screen (where the wrong-format
     * wrap would otherwise look like a wrong-PIN error forever).
     */
    fun isVaultLegacyFormat(): Boolean =
        isVaultInitialized() && prefs.getWrapFormatVersion() < WRAP_FORMAT_PEPPERED

    /**
     * First-time setup. Generates a random ChaCha20 key, wraps it under a PIN-derived
     * key (peppered + 600k iterations), and stores the wrap + salt + format version.
     * Throws if the vault is already initialized.
     */
    fun setupVault(pin: String) {
        require(pin.length in 6..10) { "PIN must be 6-10 digits" }
        require(!isVaultInitialized()) { "Vault already initialized" }

        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val pinKey = derivePinKeyPeppered(pin, salt)

        val chaChaBytes = ByteArray(CHACHA_KEY_SIZE).also { SecureRandom().nextBytes(it) }
        val wrapped = aesGcmEncrypt(chaChaBytes, pinKey)

        // Atomic write: salt + wrapped key + format version must land in the same edit,
        // otherwise a power loss between separate apply()s desynchronises them and bricks
        // the vault permanently.
        prefs.saveVaultMaterialAtomic(
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            wrappedBase64 = Base64.encodeToString(wrapped, Base64.NO_WRAP),
            wrapFormatVersion = WRAP_FORMAT_PEPPERED,
        )

        installSessionKey(chaChaBytes)
    }

    /**
     * Verify the PIN by attempting to unwrap the stored ChaCha20 key. Caches the
     * unwrapped key in memory on success. Returns false on wrong PIN (GCM tag mismatch),
     * if the vault is not initialized, if the vault was written by a pre-pepper build
     * ([isVaultLegacyFormat]), or if the user is currently inside the brute-force
     * lockout window.
     */
    fun unlockWithPin(pin: String): Boolean {
        if (prefs.isLockedOut()) return false
        if (!isVaultInitialized()) return false
        if (prefs.getWrapFormatVersion() < WRAP_FORMAT_PEPPERED) return false
        val saltStr = prefs.getPinSalt() ?: return false
        val wrappedStr = prefs.getWrappedKeyV2() ?: return false

        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        val wrapped = Base64.decode(wrappedStr, Base64.NO_WRAP)
        val pinKey = derivePinKeyPeppered(pin, salt)

        return try {
            val chaChaBytes = aesGcmDecrypt(wrapped, pinKey)
            installSessionKey(chaChaBytes)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Re-wrap the in-memory ChaCha20 key under a new PIN. Vault must be unlocked.
     * Used by Settings → Change PIN, after the user has supplied the current PIN.
     * Always writes the peppered (v2) format.
     */
    fun rewrapWithNewPin(newPin: String): Boolean {
        require(newPin.length in 6..10) { "PIN must be 6-10 digits" }
        val keyBytes = sessionChaChaBytes ?: return false
        val newSalt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val newPinKey = derivePinKeyPeppered(newPin, newSalt)
        val wrapped = aesGcmEncrypt(keyBytes, newPinKey)
        prefs.saveVaultMaterialAtomic(
            saltBase64 = Base64.encodeToString(newSalt, Base64.NO_WRAP),
            wrappedBase64 = Base64.encodeToString(wrapped, Base64.NO_WRAP),
            wrapFormatVersion = WRAP_FORMAT_PEPPERED,
        )
        // Re-encrypting the master key invalidates the old biometric wrap; wipe it.
        if (biometricStore.isEnrolled(prefs)) {
            biometricStore.disable(prefs)
        }
        return true
    }

    /** Clear the in-memory ChaCha20 key and zero the backing bytes. */
    fun lockVault() {
        synchronized(sessionLock) {
            sessionChaChaBytes?.fill(0)
            sessionChaChaBytes = null
            sessionChaChaKey = null
        }
    }

    /**
     * Run a PBKDF2 derivation with the same iteration count as a real unlock and
     * discard the result. Used by the calculator unlock path to make decoy PIN
     * attempts indistinguishable from real ones in wall-clock time.
     *
     * Uses a stable per-install dummy salt so timing doesn't vary between fresh
     * onboarding (when no real salt exists yet) and post-setup attempts.
     */
    fun runDummyKdf() {
        try {
            val salt = getOrCreateDummySalt()
            derivePinKeyPeppered("000000", salt)
        } catch (_: Exception) {
            // Best-effort; even a failed KDF still consumed time.
        }
    }

    private fun getOrCreateDummySalt(): ByteArray {
        prefs.getDummyKdfSalt()?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        prefs.saveDummyKdfSalt(Base64.encodeToString(salt, Base64.NO_WRAP))
        return salt
    }

    /**
     * Atomically swap the session key with a fresh one, zeroing the prior key
     * material. Caller transfers ownership of [bytes] to this method — do not
     * read or mutate them after the call returns.
     */
    private fun installSessionKey(bytes: ByteArray) {
        synchronized(sessionLock) {
            sessionChaChaBytes?.fill(0)
            sessionChaChaBytes = bytes
            sessionChaChaKey = SecretKeySpec(bytes, "ChaCha20")
        }
    }

    // ==================== Biometric-bound unlock ====================

    fun isBiometricUnlockEnrolled(): Boolean = biometricStore.isEnrolled(prefs)

    /**
     * Wraps the in-memory ChaCha20 key under a Keystore key that requires user
     * authentication. The wrap blob is stored, and the chacha key can later be
     * recovered only via a successful BiometricPrompt CryptoObject.
     */
    fun disableBiometricUnlock() {
        biometricStore.disable(prefs)
    }

    /**
     * Returns a Cipher initialised for biometric-bound *enrollment* (encrypt mode).
     * Caller (VaultBiometricHelper) must pass it into a BiometricPrompt CryptoObject
     * and call [finishBiometricEnroll] with the authorised cipher on success.
     */
    fun biometricEnrollCipher(): Cipher? {
        if (sessionChaChaBytes == null) return null
        return biometricStore.enrollCipher()
    }

    /** After BiometricPrompt success, wrap the in-memory ChaCha key with the authorised cipher. */
    fun finishBiometricEnroll(authorisedCipher: Cipher): Boolean {
        val keyBytes = sessionChaChaBytes ?: return false
        return biometricStore.finishEnroll(authorisedCipher, keyBytes, prefs)
    }

    /** Returns a Cipher initialised for biometric-bound decrypt; pass to BiometricPrompt. */
    fun biometricDecryptCipher(): Cipher? = biometricStore.decryptCipher(prefs)

    /**
     * After BiometricPrompt success, finishes unwrapping using the now-authorised cipher
     * and caches the chacha key in memory.
     */
    fun finishBiometricUnlock(authorisedCipher: Cipher): Boolean {
        val keyBytes = biometricStore.unwrap(authorisedCipher, prefs) ?: return false
        installSessionKey(keyBytes)
        return true
    }

    // ==================== Chunk crypto ====================

    private fun deriveChunkNonce(masterNonce: ByteArray, chunkIndex: Long): ByteArray {
        val derived = masterNonce.copyOf()
        val indexBytes = ByteBuffer.allocate(8).putLong(chunkIndex).array()
        for (i in 0 until 8) {
            derived[derived.size - 8 + i] =
                (derived[derived.size - 8 + i].toInt() xor indexBytes[i].toInt()).toByte()
        }
        return derived
    }

    fun encryptChunk(plaintext: ByteArray, masterNonce: ByteArray, chunkIndex: Long): ByteArray {
        val key = sessionChaChaKey
            ?: throw IllegalStateException("Vault is locked")
        val cipher = Cipher.getInstance(TRANSFORMATION_CHACHA_POLY)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(deriveChunkNonce(masterNonce, chunkIndex)))
        return cipher.doFinal(plaintext)
    }

    fun decryptChunk(ciphertext: ByteArray, masterNonce: ByteArray, chunkIndex: Long): ByteArray {
        val key = sessionChaChaKey
            ?: throw IllegalStateException("Vault is locked")
        val cipher = Cipher.getInstance(TRANSFORMATION_CHACHA_POLY)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(deriveChunkNonce(masterNonce, chunkIndex)))
        return cipher.doFinal(ciphertext)
    }

    fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_SIZE)
        SecureRandom().nextBytes(nonce)
        return nonce
    }

    // ==================== KDF + AES-GCM helpers ====================

    /** Peppered: HMAC-SHA256(pepperKey, PIN) -> 600k PBKDF2 iterations. The pepper key
     *  lives in Android Keystore and cannot be exported; an attacker with the encrypted
     *  prefs file but no Keystore access cannot brute-force a short PIN. */
    private fun derivePinKeyPeppered(pin: String, salt: ByteArray): SecretKey {
        val pepperedChars = pepperPin(pin)
        return runPbkdf2(pepperedChars, salt, PBKDF2_ITERATIONS_PEPPERED)
    }

    /** Shared PBKDF2-HMAC-SHA256 -> AES-256 key. Wipes the password char[] so the
     *  PIN material does not outlive this call in the heap. */
    private fun runPbkdf2(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val keyBytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
            password.fill(' ')
        }
    }

    /** Returns the Keystore HMAC-SHA256 pepper key, creating it on first use. The key
     *  bytes never leave Android Keystore; only Mac.doFinal operations are exposed. */
    private fun getOrCreatePepperKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(PEPPER_KEY_ALIAS, null)?.let { return it as SecretKey }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(PEPPER_KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return keyGen.generateKey()
    }

    /** Pepper the PIN: HMAC-SHA256(pepperKey, UTF-8(pin)) -> base64-CHAR-array.
     *  The result is used as the PBKDF2 password input. */
    private fun pepperPin(pin: String): CharArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(getOrCreatePepperKey())
        val pinBytes = pin.toByteArray(Charsets.UTF_8)
        try {
            val hashed = mac.doFinal(pinBytes)
            try {
                return Base64.encodeToString(hashed, Base64.NO_WRAP).toCharArray()
            } finally {
                hashed.fill(0)
            }
        } finally {
            pinBytes.fill(0)
        }
    }

    private fun aesGcmEncrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(GCM_IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    /** Throws AEADBadTagException on wrong key. */
    private fun aesGcmDecrypt(blob: ByteArray, key: SecretKey): ByteArray {
        val iv = blob.copyOfRange(0, GCM_IV_SIZE)
        val ct = blob.copyOfRange(GCM_IV_SIZE, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    companion object {
        @Volatile private var instance: CryptoManager? = null

        /** Process-wide singleton. The in-memory unlock state must be shared between the
         *  module's PIN unlock and [MediaRepository]'s encrypt/decrypt calls. */
        fun getInstance(context: Context): CryptoManager {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: CryptoManager(context.applicationContext).also { instance = it }
            }
        }

        const val NONCE_SIZE = 12
        const val CHUNK_SIZE = 256 * 1024
        const val AUTH_TAG_SIZE = 16
        const val ENCRYPTED_CHUNK_SIZE = CHUNK_SIZE + AUTH_TAG_SIZE

        private const val SALT_SIZE = 16
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_BITS = 128
        private const val CHACHA_KEY_SIZE = 32
        // PBKDF2 iterations for the peppered wrap format. Calibrated for ~750ms on
        // mid-range Android (Redmi 11 Pro); the async dispatch on the `=` press keeps
        // the UI responsive. Do not lower without re-running brute-force analysis.
        private const val PBKDF2_ITERATIONS_PEPPERED = 600_000

        // Wrap format versions persisted in EncryptedSharedPreferences. WRAP_FORMAT_LEGACY
        // (1) was the pre-pepper, 120k-iteration format and is no longer accepted; see
        // isVaultLegacyFormat() for the reset path. WRAP_FORMAT_PEPPERED (2) is the
        // only currently-valid wrap. Bump and add a migration path for any future change.
        const val WRAP_FORMAT_LEGACY = 1
        const val WRAP_FORMAT_PEPPERED = 2

        // Keystore alias for the HMAC-SHA256 pepper key. Generated on first peppered
        // wrap; never exportable. Wiping app data destroys it; clearing the vault from
        // Settings does not (the alias outlives the prefs wipe).
        private const val PEPPER_KEY_ALIAS = "HidaVaultPepperKey"

        private const val TRANSFORMATION_CHACHA_POLY = "ChaCha20-Poly1305"
    }
}
