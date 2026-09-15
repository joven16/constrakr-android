package com.constrakr.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Port of iOS [MatchThresholdSettings.swift].
 */
object MatchThresholdSettings {
    private const val PREFS = "constrakr.settings"
    private const val KEY_THRESHOLD = "settings.matchThreshold"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    val current: Float
        get() {
            val stored = prefs.getFloat(KEY_THRESHOLD, -1f)
            return if (stored > 0f) stored else ConsTrakrConstants.ADA_FACE_MATCH_THRESHOLD
        }

    val margin: Float get() = ConsTrakrConstants.FACE_MATCH_MARGIN

    val soloFloor: Float get() = ConsTrakrConstants.ADA_FACE_SOLO_GALLERY_THRESHOLD

    fun setThreshold(value: Float) {
        prefs.edit().putFloat(KEY_THRESHOLD, value).apply()
    }
}
