package com.constrakr.device.tracking

import android.content.Context

/** Cached diagnostics for admin UI — no coordinates in logs. */
class DeviceTrackingMetadataStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var lastCollectMillis: Long
        get() = prefs.getLong(KEY_LAST_COLLECT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_COLLECT, value).apply()

    var lastLocationMillis: Long
        get() = prefs.getLong(KEY_LAST_LOCATION, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_LOCATION, value).apply()

    var lastSuccessfulSyncMillis: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    var lastLocationLat: Double?
        get() = prefs.getString(KEY_LAT, null)?.toDoubleOrNull()
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_LAT) else putString(KEY_LAT, value.toString())
            }.apply()
        }

    var lastLocationLng: Double?
        get() = prefs.getString(KEY_LNG, null)?.toDoubleOrNull()
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_LNG) else putString(KEY_LNG, value.toString())
            }.apply()
        }

    var lastLocationAccuracy: Float?
        get() = prefs.getFloat(KEY_ACCURACY, Float.NaN).takeUnless { it.isNaN() }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_ACCURACY) else putFloat(KEY_ACCURACY, value)
            }.apply()
        }

    companion object {
        private const val PREFS = "constrakr.device.tracking.meta"
        private const val KEY_LAST_COLLECT = "last_collect"
        private const val KEY_LAST_LOCATION = "last_location"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_LAT = "last_lat"
        private const val KEY_LNG = "last_lng"
        private const val KEY_ACCURACY = "last_accuracy"
    }
}
