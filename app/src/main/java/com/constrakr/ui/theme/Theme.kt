package com.constrakr.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.constrakr.config.AppThemeMode
import com.constrakr.config.AppThemeSettings

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = TealContainer,
    secondary = TealDark,
    surface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = SurfaceMuted
)

private val DarkColors = darkColorScheme(
    primary = TealPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = TealDark,
    secondary = TealContainer
)

@Composable
fun ConsTrakrTheme(
    themeSettings: AppThemeSettings? = null,
    content: @Composable () -> Unit
) {
    val mode by (themeSettings?.mode ?: kotlinx.coroutines.flow.MutableStateFlow(AppThemeMode.SYSTEM))
        .collectAsState(initial = AppThemeMode.SYSTEM)
    val darkTheme = when (mode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    // Fixed teal/cyan palette — matches iOS (no Material You dynamic colors).
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = ConsTrakrTypography,
        content = content
    )
}
