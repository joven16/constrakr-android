package com.constrakr.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class AppPinGateState internal constructor(
    val withAppPin: (action: () -> Unit) -> Unit
)

@Composable
fun rememberAppPinGate(
    title: String = "App PIN required",
    subtitle: String = "Enter the 6-digit app PIN stored on this device."
): AppPinGateState {
    var showPin by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    AppPinSheet(
        visible = showPin,
        onDismiss = { showPin = false },
        onVerified = {
            pendingAction?.invoke()
            pendingAction = null
        },
        title = title,
        subtitle = subtitle
    )

    val withAppPin: (() -> Unit) -> Unit = { action ->
        pendingAction = action
        showPin = true
    }

    return remember(showPin, title, subtitle) {
        AppPinGateState(withAppPin)
    }
}
