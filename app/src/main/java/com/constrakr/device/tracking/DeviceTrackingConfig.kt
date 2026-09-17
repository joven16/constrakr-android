package com.constrakr.device.tracking

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Admin-controlled device fleet tracking preferences. */
class DeviceTrackingConfig(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            bump()
        }

    /** Normal interval in minutes (minimum 15). */
    var normalIntervalMinutes: Int
        get() = prefs.getInt(KEY_NORMAL_INTERVAL, DEFAULT_NORMAL_MINUTES).coerceAtLeast(MIN_INTERVAL_MINUTES)
        set(value) {
            prefs.edit().putInt(KEY_NORMAL_INTERVAL, value.coerceAtLeast(MIN_INTERVAL_MINUTES)).apply()
            bump()
        }

    /** Interval while charging or kiosk-active (minimum 15). */
    var activeIntervalMinutes: Int
        get() = prefs.getInt(KEY_ACTIVE_INTERVAL, DEFAULT_ACTIVE_MINUTES).coerceAtLeast(MIN_INTERVAL_MINUTES)
        set(value) {
            prefs.edit().putInt(KEY_ACTIVE_INTERVAL, value.coerceAtLeast(MIN_INTERVAL_MINUTES)).apply()
            bump()
        }

    fun intervalFor(charging: Boolean, kioskActive: Boolean): Long {
        val minutes = if (charging || kioskActive) activeIntervalMinutes else normalIntervalMinutes
        return minutes.coerceAtLeast(MIN_INTERVAL_MINUTES) * 60_000L
    }

    private fun bump() {
        _revision.value += 1
    }

    companion object {
        const val MIN_INTERVAL_MINUTES = 15
        const val DEFAULT_NORMAL_MINUTES = 30
        const val DEFAULT_ACTIVE_MINUTES = 15

        private const val PREFS = "constrakr.device.tracking.config"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_NORMAL_INTERVAL = "normal_interval_min"
        private const val KEY_ACTIVE_INTERVAL = "active_interval_min"
    }
}
