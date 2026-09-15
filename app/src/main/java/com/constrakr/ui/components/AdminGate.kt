package com.constrakr.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.constrakr.ConsTrakrApp
import com.constrakr.admin.AdminCodeService

class AdminGateState internal constructor(
    val withAdmin: (action: () -> Unit) -> Unit,
    val blockedMessage: String?
)

@Composable
fun rememberAdminGate(forcePromptEachTime: Boolean = false): AdminGateState {
    val container = ConsTrakrApp.instance.container
    var showAdmin by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var blockedMessage by remember { mutableStateOf<String?>(null) }

    AdminCodeSheet(
        visible = showAdmin,
        onDismiss = { showAdmin = false },
        onVerified = {
            pendingAction?.invoke()
            pendingAction = null
        }
    )

    val withAdmin: (() -> Unit) -> Unit = { action ->
        blockedMessage = null
        val gate = if (forcePromptEachTime) {
            container.adminCodeService.requirePromptForChange()
        } else {
            container.adminCodeService.ensureChangeAllowed()
        }
        when (gate) {
            AdminCodeService.AdminGateResult.Allowed -> action()
            AdminCodeService.AdminGateResult.NeedsPrompt -> {
                pendingAction = action
                showAdmin = true
            }
            is AdminCodeService.AdminGateResult.Blocked -> blockedMessage = gate.reason
        }
    }

    return remember(showAdmin, blockedMessage, forcePromptEachTime) {
        AdminGateState(withAdmin, blockedMessage)
    }
}
