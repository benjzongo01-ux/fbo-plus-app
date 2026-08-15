package com.benjo.fboplus.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

class SecureTokenStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "secure_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Données corrompues (clé Keystore désynchronisée après réinstall) : on repart de zéro
        context.deleteSharedPreferences("secure_auth_prefs")
        EncryptedSharedPreferences.create(
            context,
            "secure_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Token JWT ---

    fun saveToken(token: String) {
        prefs.edit().putString("jwt_token", token).apply()
    }

    fun getToken(): String? = prefs.getString("jwt_token", null)

    fun hasToken(): Boolean = getToken() != null

    // --- Déconnexion complète ---

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    // --- PIN (4 chiffres) ---

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun savePin(pin: String) {
        prefs.edit().putString("pin_hash", hashPin(pin)).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString("pin_hash", null) ?: return false
        return stored == hashPin(pin)
    }

    fun hasPin(): Boolean = prefs.getString("pin_hash", null) != null

    fun clearPin() {
        prefs.edit().remove("pin_hash").apply()
    }

    // --- Préférence biométrie ---

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean("biometric_enabled", false)

    // --- Verrouillage automatique ---

    fun saveLastBackgroundTime() {
        prefs.edit().putLong("last_background_time", System.currentTimeMillis()).apply()
    }

    fun shouldLock(timeoutMs: Long = 5 * 60 * 1000L): Boolean {
        val last = prefs.getLong("last_background_time", 0L)
        if (last == 0L) return false
        return System.currentTimeMillis() - last >= timeoutMs
    }

    fun clearLastBackgroundTime() {
        prefs.edit().remove("last_background_time").apply()
    }
}