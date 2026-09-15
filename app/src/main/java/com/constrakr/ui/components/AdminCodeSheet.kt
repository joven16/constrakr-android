package com.constrakr.ui.components

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import com.constrakr.ConsTrakrApp

@Composable
fun AdminCodeSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    title: String = "Admin code required",
    subtitle: String? = null
) {
    if (!visible) return

    val container = ConsTrakrApp.instance.container
    val assignedName = container.deviceStore.assignedUserName
    val resolvedSubtitle = subtitle ?: buildString {
        append("Enter the 6-digit admin code to continue.")
        if (!assignedName.isNullOrBlank()) {
            append("\n\nUse the code for $assignedName.")
        } else {
            append("\n\nUse the code from a user assigned to this device on the web.")
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PasscodeKeypad(
            title = title,
            subtitle = resolvedSubtitle,
            onCancel = onDismiss,
            onSubmit = { code ->
                container.adminCodeService.verify(code).map { name ->
                    onVerified()
                    onDismiss()
                }
            }
        )
    }
}
