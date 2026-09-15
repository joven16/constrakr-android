package com.constrakr.config

import android.content.Context
import com.constrakr.domain.FacePose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RegistrationPoseSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun isEnabled(pose: FacePose): Boolean =
        prefs.getBoolean(key(pose), true)

    fun setEnabled(pose: FacePose, enabled: Boolean) {
        if (pose == FacePose.CENTER && !enabled) return
        prefs.edit().putBoolean(key(pose), enabled).apply()
        if (enabledEnrollmentOrder().isEmpty()) {
            prefs.edit().putBoolean(key(FacePose.CENTER), true).apply()
        }
        _revision.value++
    }

    fun matchingLevel(): Level? {
        val current = enabledEnrollmentOrder().toSet()
        return Level.entries.firstOrNull { it.poses == current }
    }

    fun applyLevel(level: Level) {
        for (pose in FacePose.entries) {
            prefs.edit().putBoolean(key(pose), pose in level.poses).apply()
        }
        _revision.value++
    }

    fun enabledEnrollmentOrder(): List<FacePose> =
        FacePose.enrollmentOrder.filter { isEnabled(it) }

    enum class Level(val label: String, val poses: Set<FacePose>) {
        BASIC("Basic", setOf(FacePose.CENTER)),
        STANDARD("Standard", setOf(FacePose.CENTER, FacePose.LEFT, FacePose.RIGHT)),
        FULL("Full", FacePose.enrollmentOrder.toSet())
    }

    private fun key(pose: FacePose) = "settings.registration.pose.${pose.raw}"

    companion object {
        private const val PREFS = "constrakr.registration-poses"
    }
}
