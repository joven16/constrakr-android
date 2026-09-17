package com.constrakr.kiosk

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

/** Local 6-digit app PIN — works offline (kiosk exit / enable / disable). */
class AppPinSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasAppPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun verifyAppPin(pin: String): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_PIN_SALT, "") ?: return false
        return hash == hashPin(pin, salt)
    }

    fun setAppPin(pin: String) {
        require(pin.length == PIN_LENGTH && pin.all { it.isDigit() }) {
            "App PIN must be $PIN_LENGTH digits"
        }
        val salt = UUID.randomUUID().toString()
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hashPin(pin, salt))
            .apply()
    }

    /** First Device Owner setup — default until changed under Settings → Advanced. */
    fun ensureDefaultAppPinIfNeeded() {
        if (!hasAppPin()) setAppPin(DEFAULT_APP_PIN)
    }

    companion object {
        const val PIN_LENGTH = 6
        const val DEFAULT_APP_PIN = "882741"

        private const val PREFS = "constrakr.kiosk"
        private const val KEY_PIN_HASH = "kiosk.pin_hash"
        private const val KEY_PIN_SALT = "kiosk.pin_salt"

        private fun hashPin(pin: String, salt: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt.toByteArray())
            digest.update(pin.toByteArray())
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
