package com.constrakr.kiosk

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

/**
 * Local kiosk preferences — maintenance PIN, auto-start, kiosk enabled flag.
 */
class KioskSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var isKioskEnabled: Boolean
        get() = prefs.getBoolean(KEY_KIOSK_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_KIOSK_ENABLED, value).apply()
        }

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BOOT, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_BOOT, value).apply()
        }

    fun hasMaintenancePin(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun verifyMaintenancePin(pin: String): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_PIN_SALT, "") ?: return false
        return hash == hashPin(pin, salt)
    }

    fun setMaintenancePin(pin: String) {
        require(pin.length == PIN_LENGTH && pin.all { it.isDigit() }) {
            "Maintenance PIN must be $PIN_LENGTH digits"
        }
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: UUID.randomUUID().toString()
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hashPin(pin, salt))
            .apply()
    }

    /** First Device Owner setup — set documented default until changed in Advanced. */
    fun ensureDefaultPinIfNeeded() {
        if (!hasMaintenancePin()) {
            setMaintenancePin(DEFAULT_MAINTENANCE_PIN)
        }
    }

    companion object {
        const val PIN_LENGTH = 6
        /** Factory default — change under Settings → Advanced after provisioning. */
        const val DEFAULT_MAINTENANCE_PIN = "882741"

        private const val PREFS = "constrakr.kiosk"
        private const val KEY_KIOSK_ENABLED = "kiosk.enabled"
        private const val KEY_AUTO_BOOT = "kiosk.auto_boot"
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
