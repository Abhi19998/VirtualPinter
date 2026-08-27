package com.example.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

class SecurityAuthManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_security_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MPIN_HASH = "key_mpin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
        private const val KEY_HAS_LOGGED_IN_BEFORE = "key_has_logged_in_before"
        private const val SALT = "VirtualPdfPrinterSecuritySalt#2026"
    }

    private fun hashPin(pin: String): String {
        val input = "$SALT:$pin"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isMpinSet(): Boolean {
        val hash = prefs.getString(KEY_MPIN_HASH, null)
        return !hash.isNullOrBlank()
    }

    fun saveMpin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit()
            .putString(KEY_MPIN_HASH, hash)
            .putBoolean(KEY_HAS_LOGGED_IN_BEFORE, true)
            .apply()
    }

    fun verifyMpin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_MPIN_HASH, null) ?: return false
        val candidateHash = hashPin(pin)
        return savedHash == candidateHash
    }

    fun isBiometricEnabled(): Boolean {
        // Defaults to true if MPIN is set, allowing fingerprint unlock
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true) && isMpinSet()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun clearSecurity() {
        prefs.edit()
            .remove(KEY_MPIN_HASH)
            .remove(KEY_BIOMETRIC_ENABLED)
            .remove(KEY_HAS_LOGGED_IN_BEFORE)
            .apply()
    }
}
