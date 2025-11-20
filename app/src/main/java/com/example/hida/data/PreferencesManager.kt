package com.example.hida.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferencesManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKey,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePin(pin: String) {
        sharedPreferences.edit().putString("APP_PIN", pin).apply()
    }

    fun getPin(): String {
        return sharedPreferences.getString("APP_PIN", "1234") ?: "1234"
    }

    fun saveIconAlias(alias: String) {
        sharedPreferences.edit().putString("APP_ICON_ALIAS", alias).apply()
    }

    fun getIconAlias(): String {
        return sharedPreferences.getString("APP_ICON_ALIAS", "MainActivity") ?: "MainActivity"
    }

    fun saveFakePin(pin: String) {
        sharedPreferences.edit().putString("APP_FAKE_PIN", pin).apply()
    }

    fun getFakePin(): String {
        return sharedPreferences.getString("APP_FAKE_PIN", "") ?: "" // Default empty means disabled
    }
}
