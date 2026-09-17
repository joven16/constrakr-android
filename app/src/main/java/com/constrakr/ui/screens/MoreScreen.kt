package com.constrakr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.ui.components.ConsTrakrCard
import com.constrakr.ui.components.SiteHeader
import com.constrakr.ui.components.rememberAdminGate
import com.constrakr.util.isUserCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(
    maintenanceActive: Boolean,
    onEndMaintenance: () -> Unit,
    onJobSites: () -> Unit,
    onSettings: () -> Unit,
    onAppearance: () -> Unit
) {
    val container = ConsTrakrApp.instance.container
    val adminGate = rememberAdminGate()
    val syncStatus by container.syncCoordinator.status.collectAsState()
    val authState by container.syncCoordinator.authState.collectAsState()
    val session by container.accessSession.state.collectAsState()
    val isAdminUnlocked = session.isActive()
    LaunchedEffect(session.unlockedUntilMillis) {
        val until = session.unlockedUntilMillis ?: return@LaunchedEffect
        val wait = until - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        container.accessSession.lock()
    }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SiteHeader(
            "More",
            container.accessSession.operatorSiteTitle?.let { title -> "Operating site: $title" }
        )

        if (maintenanceActive) {
            ConsTrakrCard {
                Text(
                    "${container.kioskMaintenanceSession.remainingMinutes()} min remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(onClick = onEndMaintenance, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }

        ConsTrakrCard {
            RowItem(
                title = if (isAdminUnlocked) "Admin unlocked" else "Unlock admin",
                subtitle = session.unlockedOperatorName ?: "6-digit code · 15 min",
                icon = if (isAdminUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                onClick = {
                    if (isAdminUnlocked) {
                        container.accessSession.lock()
                        message = "Admin locked"
                    } else {
                        adminGate.withAdmin { message = "Admin unlocked for 15 minutes" }
                    }
                }
            )
            if (isAdminUnlocked) {
                Button(onClick = {
                    container.accessSession.lock()
                    message = "Admin locked"
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Lock admin now")
                }
            }
        }

        ConsTrakrCard {
            Text("Sync Account", style = MaterialTheme.typography.titleMedium)
            if (authState.sessionExpired) {
                Text(
                    "Session expired — please sign in again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (!authState.username.isNullOrBlank()) {
                    Text(
                        "Last signed in as ${authState.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                OutlinedTextField(
                    value = user.ifBlank { authState.username.orEmpty() },
                    onValueChange = { user = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Button(onClick = {
                    container.applicationScope.launch {
                        container.syncCoordinator.login(
                            user.ifBlank { authState.username.orEmpty() },
                            pass
                        )
                            .onSuccess { message = it }
                            .onFailure { error ->
                                if (!error.isUserCancellation()) message = error.message
                            }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in again")
                }
            } else if (authState.isSignedIn) {
                Text(
                    "Signed in as ${authState.username ?: "Admin"}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    "Device: ${container.deviceStore.deviceName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "ID: ${container.deviceStore.localId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(onClick = {
                    container.applicationScope.launch {
                        container.syncCoordinator.syncPending()
                            .onFailure { error ->
                                if (!error.isUserCancellation()) message = error.message
                            }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sync now")
                }
                Button(
                    onClick = {
                        adminGate.withAdmin {
                            container.syncCoordinator.signOut()
                            user = ""
                            pass = ""
                            message = "Signed out"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign out")
                }
            } else {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Text(
                    "Device ID: ${container.deviceStore.localId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Button(onClick = {
                    container.applicationScope.launch {
                        container.syncCoordinator.login(user, pass)
                            .onSuccess { message = it }
                            .onFailure { error ->
                                if (!error.isUserCancellation()) message = error.message
                            }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in")
                }
            }
            syncStatus?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            Text(
                if (authState.isSignedIn) {
                    "Sync session stays signed in on this device. Sign in again only if expired."
                } else {
                    "Use your sync account to upload attendance and employees to the server."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        ConsTrakrCard {
            RowItem("Appearance", "Theme", onClick = onAppearance)
        }

        if (isAdminUnlocked) {
            ConsTrakrCard {
                RowItem("Job Sites", "GPS pins & default site", onClick = onJobSites)
            }
        }

        if (isAdminUnlocked) {
            ConsTrakrCard {
                RowItem("Settings", "Scanner & diagnostics", onClick = onSettings)
            }
        }

        adminGate.blockedMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun RowItem(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.padding(end = 12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null)
    }
}
