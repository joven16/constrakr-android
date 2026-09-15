package com.constrakr.kiosk

import android.content.Context

/** Local kiosk preferences — auto-start and kiosk enabled flag. */
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

    companion object {
        private const val PREFS = "constrakr.kiosk"
        private const val KEY_KIOSK_ENABLED = "kiosk.enabled"
        private const val KEY_AUTO_BOOT = "kiosk.auto_boot"
    }
}
