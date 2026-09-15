package com.constrakr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.kiosk.KioskSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenancePinSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    if (!visible) return
    val settings = ConsTrakrApp.instance.container.kioskSettings
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= KioskSettings.PIN_LENGTH && it.all { c -> c.isDigit() }) pin = it
                },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        if (settings.verifyMaintenancePin(pin)) {
                            error = null
                            onVerified()
                            onDismiss()
                        } else {
                            error = "Incorrect PIN"
                        }
                    },
                    enabled = pin.length == KioskSettings.PIN_LENGTH,
                    modifier = Modifier.weight(1f)
                ) { Text("Unlock") }
            }
        }
    }
}
