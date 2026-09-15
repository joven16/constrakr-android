package com.constrakr.domain

import android.content.Context

class SiteGeofenceSettings(
    private val context: Context,
    private val jobSiteStore: JobSiteStore
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    val isRequired: Boolean
        get() = isEnabled && (jobSiteStore.defaultSite?.hasCoordinate == true)

    val defaultSiteName: String
        get() = jobSiteStore.defaultSite?.displayTitle ?: "No site configured"

    companion object {
        private const val PREFS = "constrakr.geofence"
        private const val KEY_ENABLED = "settings.siteGeofenceEnabled"
    }
}
