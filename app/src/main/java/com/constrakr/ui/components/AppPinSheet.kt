package com.constrakr.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.constrakr.ConsTrakrApp

@Composable
fun AppPinSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    title: String = "App PIN required",
    subtitle: String = "Enter the 6-digit app PIN stored on this device."
) {
    if (!visible) return

    val appPin = ConsTrakrApp.instance.container.appPinSettings

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PasscodeKeypad(
            title = title,
            subtitle = subtitle,
            onCancel = onDismiss,
            onSubmit = { pin ->
                if (appPin.verifyAppPin(pin)) {
                    Result.success(Unit).also {
                        onVerified()
                        onDismiss()
                    }
                } else {
                    Result.failure(IllegalArgumentException("Incorrect app PIN."))
                }
            }
        )
    }
}
