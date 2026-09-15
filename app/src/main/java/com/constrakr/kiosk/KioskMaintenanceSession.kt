package com.constrakr.kiosk

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Temporary exit from Lock Task for Wi‑Fi, updates, or diagnostics.
 * Hidden entry: tap ConsTrakr logo 7× → maintenance PIN.
 */
class KioskMaintenanceSession(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _isActive = MutableStateFlow(readActive())
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun refresh() {
        _isActive.value = readActive()
    }

    fun unlock(durationMs: Long = DEFAULT_UNLOCK_MS) {
        prefs.edit()
            .putLong(KEY_UNTIL, System.currentTimeMillis() + durationMs)
            .apply()
        refresh()
    }

    fun lock() {
        prefs.edit().remove(KEY_UNTIL).apply()
        refresh()
    }

    fun remainingMinutes(): Int {
        val until = prefs.getLong(KEY_UNTIL, 0L)
        if (until <= System.currentTimeMillis()) return 0
        return ((until - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(1)
    }

    private fun readActive(): Boolean {
        val until = prefs.getLong(KEY_UNTIL, 0L)
        if (until <= System.currentTimeMillis()) {
            if (until > 0L) prefs.edit().remove(KEY_UNTIL).apply()
            return false
        }
        return true
    }

    companion object {
        private const val PREFS = "constrakr.kiosk.maintenance"
        private const val KEY_UNTIL = "untilMillis"
        private const val DEFAULT_UNLOCK_MS = 15 * 60 * 1000L
    }
}
