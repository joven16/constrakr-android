package com.constrakr.kiosk

import android.content.Context

/** Tracks one-time rescue token ids consumed on this device. */
class KioskRescueTokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isConsumed(jti: String): Boolean = prefs.getBoolean(key(jti), false)

    fun markConsumed(jti: String) {
        prefs.edit().putBoolean(key(jti), true).apply()
    }

    private fun key(jti: String) = "used_$jti"

    companion object {
        private const val PREFS = "constrakr.rescue.used"
    }
}
