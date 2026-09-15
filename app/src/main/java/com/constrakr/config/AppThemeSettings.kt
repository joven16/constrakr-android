package com.constrakr.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

class AppThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(load())
    val mode: StateFlow<AppThemeMode> = _mode.asStateFlow()

    fun set(mode: AppThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
        _mode.value = mode
    }

    private fun load(): AppThemeMode =
        runCatching { AppThemeMode.valueOf(prefs.getString(KEY, AppThemeMode.SYSTEM.name)!!) }
            .getOrDefault(AppThemeMode.SYSTEM)

    companion object {
        private const val PREFS = "constrakr.appearance"
        private const val KEY = "themeMode"
    }
}
