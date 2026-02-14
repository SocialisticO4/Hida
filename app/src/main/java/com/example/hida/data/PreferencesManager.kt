package com.example.hida.data

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

    // ==================== PIN Management ====================
    
    fun savePin(pin: String) {
        if (pin.length < 4) return // Enforce minimum length
        sharedPreferences.edit().putString(KEY_PIN, pin).apply()
    }

    fun getPin(): String? {
        // No default PIN - returns null if not set (forces Welcome screen)
        return sharedPreferences.getString(KEY_PIN, null)
    }
    
    fun hasPin(): Boolean {
        return getPin() != null
    }

    fun saveFakePin(pin: String) {
        if (pin.length < 4 && pin.isNotEmpty()) return // Enforce minimum if not empty
        sharedPreferences.edit().putString(KEY_FAKE_PIN, pin).apply()
    }

    fun getFakePin(): String {
        return sharedPreferences.getString(KEY_FAKE_PIN, "") ?: ""
    }

    // ==================== Icon Alias ====================
    
    fun saveIconAlias(alias: String) {
        sharedPreferences.edit().putString(KEY_ICON_ALIAS, alias).apply()
    }

    fun getIconAlias(): String {
        return sharedPreferences.getString(KEY_ICON_ALIAS, "MainActivity") ?: "MainActivity"
    }

    // ==================== First Launch ====================
    
    fun isFirstLaunch(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchComplete() {
        sharedPreferences.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
    }

    fun getWrappedKey(): String? {
        return sharedPreferences.getString(KEY_WRAPPED_KEY, null)
    }

    fun saveWrappedKey(wrappedKey: String) {
        sharedPreferences.edit().putString(KEY_WRAPPED_KEY, wrappedKey).apply()
    }

    // ==================== Brute Force Protection ====================
    
    private companion object {
        const val KEY_PIN = "APP_PIN"
        const val KEY_FAKE_PIN = "APP_FAKE_PIN"
        const val KEY_ICON_ALIAS = "APP_ICON_ALIAS"
        const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        const val KEY_WRAPPED_KEY = "wrapped_encryption_key"
        
        // Brute force protection
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_LOCKOUT_END_TIME = "LOCKOUT_END_TIME"
        const val MAX_ATTEMPTS_BEFORE_DELAY = 3
        const val BASE_LOCKOUT_MS = 5000L // 5 seconds
        const val MAX_LOCKOUT_MS = 300000L // 5 minutes max
        
        // Session management
        const val KEY_LAST_ACTIVITY = "LAST_ACTIVITY_TIME"
        const val KEY_SESSION_TIMEOUT = "SESSION_TIMEOUT_MS"
        const val KEY_WAS_PAUSED = "WAS_APP_PAUSED"
        const val DEFAULT_SESSION_TIMEOUT = 60000L // 1 minute default
    }

    fun recordFailedAttempt(): Long {
        val attempts = getFailedAttempts() + 1
        sharedPreferences.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()
        
        // Calculate exponential backoff: 5s, 10s, 20s, 40s... up to 5 min
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
    
    // Auto-lock timeout options (in milliseconds)
    // 0 = Immediately lock when going to background
    enum class AutoLockTimeout(val ms: Long, val label: String) {
        IMMEDIATELY(0L, "Immediately"),
        SECONDS_15(15000L, "15 seconds"),
        SECONDS_30(30000L, "30 seconds"),
        SECONDS_45(45000L, "45 seconds"),
        MINUTE_1(60000L, "1 minute"),
        MINUTES_2(120000L, "2 minutes"),
        MINUTES_5(300000L, "5 minutes"),
        MINUTES_15(900000L, "15 minutes")
    }

    fun setSessionTimeout(timeoutMs: Long) {
        sharedPreferences.edit()
            .putLong(KEY_SESSION_TIMEOUT, timeoutMs)
            .apply()
    }

    fun getSessionTimeout(): Long {
        return sharedPreferences.getLong(KEY_SESSION_TIMEOUT, DEFAULT_SESSION_TIMEOUT)
    }

    /**
     * Called when app goes to background (ON_PAUSE).
     * Records the timestamp and marks app as paused.
     */
    fun markAppPaused() {
        sharedPreferences.edit()
            .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
            .putBoolean(KEY_WAS_PAUSED, true)
            .apply()
    }
    
    /**
     * Called when app returns to foreground (ON_RESUME).
     * Clears the paused flag.
     */
    fun clearPausedFlag() {
        sharedPreferences.edit()
            .putBoolean(KEY_WAS_PAUSED, false)
            .apply()
    }
    
    /**
     * Check if app was paused (went to background).
     */
    fun wasAppPaused(): Boolean {
        return sharedPreferences.getBoolean(KEY_WAS_PAUSED, false)
    }

    /**
     * Check if session expired based on timeout setting.
     * - Immediately (0): Expires if app was paused at all
     * - Other timeouts: Expires if elapsed time > timeout
     */
    fun isSessionExpired(): Boolean {
        val timeout = getSessionTimeout()
        val wasPaused = wasAppPaused()
        
        // If not paused, session is still valid (app just opened)
        if (!wasPaused) return false
        
        // Immediately: Lock as soon as app returns from background
        if (timeout == 0L) return true
        
        // Check elapsed time for non-zero timeouts
        val lastActivity = sharedPreferences.getLong(KEY_LAST_ACTIVITY, 0)
        if (lastActivity == 0L) return true
        return System.currentTimeMillis() - lastActivity > timeout
    }

    fun clearSession() {
        sharedPreferences.edit()
            .putLong(KEY_LAST_ACTIVITY, 0)
            .putBoolean(KEY_WAS_PAUSED, false)
            .apply()
    }
}
