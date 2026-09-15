package com.constrakr.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FaceScanStep(val raw: String, val label: String) {
    CLOSE_UP("close_up", "Move closer"),
    LOOK_LEFT("look_left", "Look left"),
    LOOK_RIGHT("look_right", "Look right"),
    LOOK_UP("look_up", "Look up"),
    LOOK_DOWN("look_down", "Look down");

    companion object {
        val ordered = listOf(LOOK_LEFT, LOOK_RIGHT, LOOK_UP, LOOK_DOWN, CLOSE_UP)
    }
}

class FaceScanSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun isEnabled(step: FaceScanStep): Boolean =
        prefs.getBoolean("settings.faceScan.step.${step.raw}", defaultFor(step))

    fun setEnabled(step: FaceScanStep, enabled: Boolean) {
        prefs.edit().putBoolean("settings.faceScan.step.${step.raw}", enabled).apply()
        _revision.value++
    }

    fun applyLevel(level: Level) {
        for (step in FaceScanStep.entries) {
            setEnabled(step, step in level.steps)
        }
    }

    fun enabledSteps(): List<FaceScanStep> =
        FaceScanStep.ordered.filter { isEnabled(it) }

    enum class Level(val label: String, val steps: Set<FaceScanStep>) {
        BASIC("Basic", setOf(FaceScanStep.CLOSE_UP)),
        STANDARD("Standard", setOf(FaceScanStep.CLOSE_UP, FaceScanStep.LOOK_LEFT, FaceScanStep.LOOK_RIGHT)),
        FULL("Full", FaceScanStep.ordered.toSet())
    }

    fun matchingLevel(): Level? {
        val current = enabledSteps().toSet()
        return Level.entries.firstOrNull { it.steps == current }
    }

    private fun defaultFor(step: FaceScanStep): Boolean = true

    companion object {
        private const val PREFS = "constrakr.face-scan"
    }
}
