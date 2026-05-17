package app.hida.vault.nativemodule

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferencesManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ==================== Vault crypto (PIN-bound) ====================

    /**
     * The plaintext PIN is no longer stored. The PIN derives a wrapping key (PBKDF2)
     * which encrypts the file ChaCha20 key. Wrong PIN → AES/GCM tag mismatch on unwrap.
     * See [CryptoManager.unlockWithPin].
     */

    fun getPinSalt(): String? = sharedPreferences.getString(KEY_PIN_SALT, null)

    fun savePinSalt(saltBase64: String) {
        sharedPreferences.edit().putString(KEY_PIN_SALT, saltBase64).apply()
    }

    fun getWrappedKeyV2(): String? = sharedPreferences.getString(KEY_WRAPPED_KEY_V2, null)

    fun saveWrappedKeyV2(wrappedBase64: String) {
        sharedPreferences.edit().putString(KEY_WRAPPED_KEY_V2, wrappedBase64).apply()
    }

    /**
     * Wrap-format version. See [CryptoManager.WRAP_FORMAT_PEPPERED] / [CryptoManager.WRAP_FORMAT_LEGACY].
     * Default 1 for backward compatibility with vaults created before the migration.
     */
    fun getWrapFormatVersion(): Int =
        sharedPreferences.getInt(KEY_WRAP_FORMAT_VERSION, 1)

    /**
     * Atomically write the salt, wrapped-key, and wrap-format version in a single
     * edit. Required because a power loss or kill between separate writes would
     * desynchronise them and brick the vault permanently (wrong-PIN wrap forever
     * or version reading the wrong KDF). Used by every path that produces a new
     * wrap: setupVault, rewrapWithNewPin, and the v1→v2 auto-migration.
     */
    fun saveVaultMaterialAtomic(
        saltBase64: String,
        wrappedBase64: String,
        wrapFormatVersion: Int,
    ) {
        sharedPreferences.edit()
            .putString(KEY_PIN_SALT, saltBase64)
            .putString(KEY_WRAPPED_KEY_V2, wrappedBase64)
            .putInt(KEY_WRAP_FORMAT_VERSION, wrapFormatVersion)
            .apply()
    }

    /** Per-install dummy salt for [CryptoManager.runDummyKdf]; created lazily on first call. */
    fun getDummyKdfSalt(): String? = sharedPreferences.getString(KEY_DUMMY_KDF_SALT, null)

    fun saveDummyKdfSalt(saltBase64: String) {
        sharedPreferences.edit().putString(KEY_DUMMY_KDF_SALT, saltBase64).apply()
    }

    fun hasPin(): Boolean = getPinSalt() != null && getWrappedKeyV2() != null

    /** True iff this install still carries v1 (Keystore-only) crypto state. */
    fun hasLegacyV1State(): Boolean =
        sharedPreferences.contains(KEY_LEGACY_PIN) ||
            sharedPreferences.contains(KEY_LEGACY_WRAPPED_KEY)

    /** One-time wipe of v1 state on upgrade. Called by the install init flow. */
    fun clearLegacyV1State() {
        sharedPreferences.edit()
            .remove(KEY_LEGACY_PIN)
            .remove(KEY_LEGACY_WRAPPED_KEY)
            .apply()
    }

    /** Wipes everything PIN/key/biometric so the user can re-onboard from scratch. */
    fun wipeVaultCrypto() {
        sharedPreferences.edit()
            .remove(KEY_PIN_SALT)
            .remove(KEY_WRAPPED_KEY_V2)
            .remove(KEY_WRAP_FORMAT_VERSION)
            .remove(KEY_BIOMETRIC_WRAPPED_KEY)
            .remove(KEY_BIOMETRIC_WRAPPED_IV)
            .remove(KEY_DUMMY_KDF_SALT)
            .remove(KEY_LEGACY_PIN)
            .remove(KEY_LEGACY_WRAPPED_KEY)
            .remove(KEY_PIN_LENGTH)
            .apply()
    }

    // ==================== Decoy PIN (UI-only) ====================
    //
    // The decoy PIN is a UI-level toggle that routes the user to an empty fake
    // gallery (see app/gallery.tsx). It is not a cryptographic boundary — there is
    // no separate encrypted vault for the decoy. Storing it as a plaintext string
    // inside EncryptedSharedPreferences is intentional.

    fun saveFakePin(pin: String) {
        if (pin.length < 4 && pin.isNotEmpty()) return
        sharedPreferences.edit().putString(KEY_FAKE_PIN, pin).apply()
    }

    fun getFakePin(): String {
        return sharedPreferences.getString(KEY_FAKE_PIN, "") ?: ""
    }

    // ==================== Biometric-bound key wrap ====================

    fun getBiometricWrappedKey(): String? =
        sharedPreferences.getString(KEY_BIOMETRIC_WRAPPED_KEY, null)

    fun saveBiometricWrappedKey(wrappedBase64: String, ivBase64: String) {
        sharedPreferences.edit()
            .putString(KEY_BIOMETRIC_WRAPPED_KEY, wrappedBase64)
            .putString(KEY_BIOMETRIC_WRAPPED_IV, ivBase64)
            .apply()
    }

    fun getBiometricWrappedIv(): String? =
        sharedPreferences.getString(KEY_BIOMETRIC_WRAPPED_IV, null)

    fun clearBiometricWrappedKey() {
        sharedPreferences.edit()
            .remove(KEY_BIOMETRIC_WRAPPED_KEY)
            .remove(KEY_BIOMETRIC_WRAPPED_IV)
            .apply()
    }

    // ==================== Icon Alias ====================

    fun saveIconAlias(alias: String) {
        sharedPreferences.edit().putString(KEY_ICON_ALIAS, alias).apply()
    }

    fun getIconAlias(): String {
        return sharedPreferences.getString(KEY_ICON_ALIAS, "HidaDefaultAlias") ?: "HidaDefaultAlias"
    }

    // ==================== First Launch ====================

    fun isFirstLaunch(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchComplete() {
        sharedPreferences.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
    }

    // ==================== Brute Force Protection ====================

    private companion object {
        // v1 (legacy) — plaintext PIN + Keystore-only wrapped key. Kept here for one-time
        // wipe on upgrade; never read for new operations.
        const val KEY_LEGACY_PIN = "APP_PIN"
        const val KEY_LEGACY_WRAPPED_KEY = "wrapped_encryption_key"

        // v2 — PIN-bound key wrap.
        const val KEY_PIN_SALT = "pin_salt_v2"
        const val KEY_WRAPPED_KEY_V2 = "wrapped_chacha_key_v2"
        const val KEY_BIOMETRIC_WRAPPED_KEY = "biometric_wrapped_key_v2"
        const val KEY_BIOMETRIC_WRAPPED_IV = "biometric_wrapped_iv_v2"
        const val KEY_DUMMY_KDF_SALT = "dummy_kdf_salt_v2"
        // Wrap-format version: see CryptoManager.WRAP_FORMAT_PEPPERED for value semantics.
        const val KEY_WRAP_FORMAT_VERSION = "wrap_format_version"

        const val KEY_FAKE_PIN = "APP_FAKE_PIN"
        const val KEY_ICON_ALIAS = "APP_ICON_ALIAS"
        const val KEY_IS_FIRST_LAUNCH = "is_first_launch"

        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_LOCKOUT_END_TIME = "LOCKOUT_END_TIME"
        const val MAX_ATTEMPTS_BEFORE_DELAY = 3
        const val BASE_LOCKOUT_MS = 5000L
        const val MAX_LOCKOUT_MS = 300000L

        const val KEY_LAST_ACTIVITY = "LAST_ACTIVITY_TIME"
        const val KEY_SESSION_TIMEOUT = "SESSION_TIMEOUT_MS"
        /** Persisted sentinel: lock when screen turns off (replaces legacy 0 “Never”). */
        const val SESSION_TIMEOUT_LOCK_ON_DEVICE = -1L
        /** True while RN reports a vault screen is visible (excluding calculator/welcome). */
        const val KEY_VAULT_ROUTE_ACTIVE = "VAULT_ROUTE_ACTIVE"
        /** Set by ACTION_SCREEN_OFF when lock-on-device mode is on and vault is open. Cleared by clearSession. */
        const val KEY_LOCK_PENDING_SCREEN_OFF = "LOCK_PENDING_SCREEN_OFF"
        const val KEY_WAS_PAUSED = "WAS_APP_PAUSED"
        const val KEY_BIOMETRIC_ENABLED = "BIOMETRIC_ENABLED"
        const val KEY_SOUND_ENABLED = "SOUND_ENABLED"
        const val KEY_THEME_MODE = "THEME_MODE"
        const val KEY_UNLOCK_STYLE = "UNLOCK_STYLE"
        const val KEY_PIN_LENGTH = "PIN_LENGTH"
        const val DEFAULT_SESSION_TIMEOUT = 60000L
    }

    // ==================== Unlock style (calculator vs plain keypad) ====================

    /** Stored value: "calculator" (default) | "keypad". Chosen during welcome. */
    fun getUnlockStyle(): String {
        return sharedPreferences.getString(KEY_UNLOCK_STYLE, "calculator") ?: "calculator"
    }

    fun setUnlockStyle(style: String) {
        val safe = when (style) {
            "calculator", "keypad" -> style
            else -> "calculator"
        }
        sharedPreferences.edit().putString(KEY_UNLOCK_STYLE, safe).apply()
    }

    /**
     * PIN length, written by setupVault / changePin. Needed by the plain-keypad
     * unlock screen so it can auto-submit when the user has typed the expected
     * number of digits (the indicator deliberately doesn't leak length).
     * Returns 0 if no PIN has been set.
     */
    fun getPinLength(): Int = sharedPreferences.getInt(KEY_PIN_LENGTH, 0)

    fun setPinLength(length: Int) {
        sharedPreferences.edit().putInt(KEY_PIN_LENGTH, length).apply()
    }

    // ==================== Theme (vault RN screens) ====================

    /** Stored value: "system" | "light" | "dark" */
    fun getThemeMode(): String {
        return sharedPreferences.getString(KEY_THEME_MODE, "system") ?: "system"
    }

    fun setThemeMode(mode: String) {
        val safe = when (mode) {
            "light", "dark", "system" -> mode
            else -> "system"
        }
        sharedPreferences.edit().putString(KEY_THEME_MODE, safe).apply()
    }

    fun recordFailedAttempt(): Long {
        val attempts = getFailedAttempts() + 1
        sharedPreferences.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()

        if (attempts >= MAX_ATTEMPTS_BEFORE_DELAY) {
            val delayMultiplier = attempts - MAX_ATTEMPTS_BEFORE_DELAY + 1
            val lockoutDuration = (BASE_LOCKOUT_MS * (1 shl (delayMultiplier - 1).coerceAtMost(6)))
                .coerceAtMost(MAX_LOCKOUT_MS)
            val lockoutEndTime = System.currentTimeMillis() + lockoutDuration
            sharedPreferences.edit().putLong(KEY_LOCKOUT_END_TIME, lockoutEndTime).apply()
            return lockoutDuration
        }
        return 0
    }

    fun getFailedAttempts(): Int {
        return sharedPreferences.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    fun getRemainingLockoutTime(): Long {
        val lockoutEndTime = sharedPreferences.getLong(KEY_LOCKOUT_END_TIME, 0)
        val remaining = lockoutEndTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    fun isLockedOut(): Boolean {
        return getRemainingLockoutTime() > 0
    }

    fun clearFailedAttempts() {
        sharedPreferences.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_END_TIME, 0)
            .apply()
    }

    // ==================== Session Management ====================

    fun setSessionTimeout(timeoutMs: Long) {
        val safe = when {
            timeoutMs == SESSION_TIMEOUT_LOCK_ON_DEVICE -> SESSION_TIMEOUT_LOCK_ON_DEVICE
            timeoutMs > 0L -> timeoutMs
            else -> DEFAULT_SESSION_TIMEOUT
        }
        sharedPreferences.edit()
            .putLong(KEY_SESSION_TIMEOUT, safe)
            .apply()
    }

    fun getSessionTimeout(): Long {
        val raw = sharedPreferences.getLong(KEY_SESSION_TIMEOUT, DEFAULT_SESSION_TIMEOUT)
        // Legacy stored "Never" (0 ms) migrated to lock when device sleeps.
        return if (raw == 0L) SESSION_TIMEOUT_LOCK_ON_DEVICE else raw
    }

    fun markAppPaused() {
        sharedPreferences.edit()
            .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
            .putBoolean(KEY_WAS_PAUSED, true)
            .apply()
    }

    fun clearPausedFlag() {
        sharedPreferences.edit()
            .putBoolean(KEY_WAS_PAUSED, false)
            .apply()
    }

    fun wasAppPaused(): Boolean {
        return sharedPreferences.getBoolean(KEY_WAS_PAUSED, false)
    }

    fun isSessionExpired(): Boolean {
        if (isLockPendingScreenOff()) return true

        val timeout = getSessionTimeout()
        val wasPaused = wasAppPaused()

        if (!wasPaused) return false
        if (timeout <= 0L) return false

        val lastActivity = sharedPreferences.getLong(KEY_LAST_ACTIVITY, 0)
        if (lastActivity == 0L) return true
        val elapsed = System.currentTimeMillis() - lastActivity
        return elapsed > timeout
    }

    /** RN toggles whether a vault UI route is foreground (gallery, viewers, settings). */
    fun setVaultUnlockedRouteActive(active: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_VAULT_ROUTE_ACTIVE, active).apply()
    }

    fun isVaultUnlockedRouteActive(): Boolean {
        return sharedPreferences.getBoolean(KEY_VAULT_ROUTE_ACTIVE, false)
    }

    /**
     * Invoked from ACTION_SCREEN_OFF when [SESSION_TIMEOUT_LOCK_ON_DEVICE] is selected
     * and the user was inside the vault.
     */
    fun markScreenOffLockPendingIfApplicable() {
        if (getSessionTimeout() != SESSION_TIMEOUT_LOCK_ON_DEVICE) return
        if (!isVaultUnlockedRouteActive()) return
        sharedPreferences.edit().putBoolean(KEY_LOCK_PENDING_SCREEN_OFF, true).apply()
    }

    fun isLockPendingScreenOff(): Boolean {
        return sharedPreferences.getBoolean(KEY_LOCK_PENDING_SCREEN_OFF, false)
    }

    fun clearSession() {
        sharedPreferences.edit()
            .putLong(KEY_LAST_ACTIVITY, 0)
            .putBoolean(KEY_WAS_PAUSED, false)
            .putBoolean(KEY_LOCK_PENDING_SCREEN_OFF, false)
            .apply()
    }

    // ==================== Biometric ====================

    fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    // ==================== Sound ====================

    fun setSoundEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    fun isSoundEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SOUND_ENABLED, false)
    }
}
