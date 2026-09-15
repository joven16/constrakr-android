package com.constrakr.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.config.AppThemeMode
import com.constrakr.config.FaceScanSettings
import com.constrakr.config.FaceScanStep
import com.constrakr.config.RegistrationPoseSettings
import com.constrakr.domain.FacePose
import com.constrakr.kiosk.KioskController
import com.constrakr.kiosk.KioskSettings
import com.constrakr.ui.components.AdminGateState
import com.constrakr.ui.components.ConsTrakrCard
import com.constrakr.ui.components.SyncPullToRefreshBox
import com.constrakr.ui.components.rememberAdminGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(onBack: () -> Unit, onScanner: () -> Unit, onAdvanced: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        SyncPullToRefreshBox(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConsTrakrCard {
                    Text("Configuration")
                    androidx.compose.material3.TextButton(onClick = onScanner, modifier = Modifier.fillMaxWidth()) {
                        Text("Scanner & registration")
                    }
                    androidx.compose.material3.TextButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
                        Text("Advanced & diagnostics")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScannerScreen(onBack: () -> Unit) {
    val container = ConsTrakrApp.instance.container
    val adminGate = rememberAdminGate(forcePromptEachTime = true)
    val poseRev by container.registrationPoseSettings.revision.collectAsState()
    val scanRev by container.faceScanSettings.revision.collectAsState()
    val poseLevel = remember(poseRev) { container.registrationPoseSettings.matchingLevel() }
    val scanLevel = remember(scanRev) { container.faceScanSettings.matchingLevel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scanner settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            adminGate.blockedMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            ConsTrakrCard {
                Text("Registration poses")
                Text(
                    "Poses used when enrolling a new employee.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                RegistrationPoseSettings.Level.entries.forEach { level ->
                    FilterChip(
                        selected = poseLevel == level,
                        onClick = { adminGate.withAdmin { container.registrationPoseSettings.applyLevel(level) } },
                        label = { Text(level.label) },
                        modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                    )
                }
                FacePose.enrollmentOrder.forEach { pose ->
                    val enabled = container.registrationPoseSettings.isEnabled(pose)
                    AdminRowSwitch(
                        adminGate = adminGate,
                        label = pose.displayName,
                        checked = enabled,
                        enabled = pose != FacePose.CENTER || enabled
                    ) {
                        container.registrationPoseSettings.setEnabled(pose, it)
                    }
                }
            }
            ConsTrakrCard {
                Text("Face scan steps (attendance)")
                FaceScanSettings.Level.entries.forEach { level ->
                    FilterChip(
                        selected = scanLevel == level,
                        onClick = { adminGate.withAdmin { container.faceScanSettings.applyLevel(level) } },
                        label = { Text(level.label) },
                        modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                    )
                }
                FaceScanStep.ordered.forEach { step ->
                    val enabled = container.faceScanSettings.isEnabled(step)
                    AdminRowSwitch(adminGate, step.label, enabled) {
                        container.faceScanSettings.setEnabled(step, it)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearanceScreen(onBack: () -> Unit) {
    val container = ConsTrakrApp.instance.container
    val adminGate = rememberAdminGate(forcePromptEachTime = true)
    val mode by container.themeSettings.mode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            adminGate.blockedMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            AppThemeMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { adminGate.withAdmin { container.themeSettings.set(m) } },
                    label = { Text(m.name.lowercase().replaceFirstChar { it.titlecase() }) },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAdvancedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val container = ConsTrakrApp.instance.container
    val adminGate = rememberAdminGate(forcePromptEachTime = true)
    val app = ConsTrakrApp.instance
    val kiosk = remember { KioskController(context) }
    val maintenanceActive by container.kioskMaintenanceSession.isActive.collectAsState()
    val settings = container.kioskSettings

    var kioskEnabled by remember { mutableStateOf(settings.isKioskEnabled) }
    var autoBoot by remember { mutableStateOf(settings.autoStartOnBoot) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            adminGate.blockedMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            ConsTrakrCard {
                Text("Device ID: ${container.deviceStore.localId}")
                Text("Device name: ${container.deviceStore.deviceName}")
            }
            ConsTrakrCard {
                Text("Diagnostics")
                Text("AdaFace: ${if (app.adaFaceRecognizer.isReady) "loaded" else "missing"}")
                Text("MiniFAS: ${if (app.miniFasDetector.isReady) "loaded" else "missing"}")
                Text("Device owner: ${kiosk.isDeviceOwner}")
                if (kiosk.isDeviceOwner && maintenanceActive) {
                    Text(
                        "${container.kioskMaintenanceSession.remainingMinutes()} min remaining",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (kiosk.isDeviceOwner) {
                ConsTrakrCard {
                    Text("Device lock", style = MaterialTheme.typography.titleMedium)
                    AdminRowSwitch(adminGate, "Enabled", kioskEnabled) {
                        kioskEnabled = it
                        settings.isKioskEnabled = it
                        if (it && !maintenanceActive) {
                            kiosk.enterKioskIfNeeded(activity)
                        } else if (!it) {
                            kiosk.exitKioskForMaintenance(activity)
                        }
                    }
                    AdminRowSwitch(adminGate, "Auto-launch on boot", autoBoot) {
                        autoBoot = it
                        settings.autoStartOnBoot = it
                    }
                    if (maintenanceActive) {
                        OutlinedButton(
                            onClick = {
                                adminGate.withAdmin {
                                    container.kioskMaintenanceSession.lock()
                                    kiosk.restoreKioskPolicies(activity)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Done") }
                    } else if (!kiosk.isInLockTask(activity) && kioskEnabled) {
                        Button(
                            onClick = { adminGate.withAdmin { kiosk.enterKioskIfNeeded(activity) } },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enable now") }
                    }
                }
                ConsTrakrCard {
                    Text("Device PIN", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        currentPin,
                        { if (it.length <= KioskSettings.PIN_LENGTH) currentPin = it.filter { c -> c.isDigit() } },
                        label = { Text("Current PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        newPin,
                        { if (it.length <= KioskSettings.PIN_LENGTH) newPin = it.filter { c -> c.isDigit() } },
                        label = { Text("New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        confirmPin,
                        { if (it.length <= KioskSettings.PIN_LENGTH) confirmPin = it.filter { c -> c.isDigit() } },
                        label = { Text("Confirm new PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    pinMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    }
                    Button(
                        onClick = {
                            adminGate.withAdmin {
                                pinMessage = when {
                                    newPin.length != KioskSettings.PIN_LENGTH -> "New PIN must be 6 digits"
                                    newPin != confirmPin -> "PINs do not match"
                                    !settings.verifyMaintenancePin(currentPin) -> "Current PIN is incorrect"
                                    else -> {
                                        settings.setMaintenancePin(newPin)
                                        currentPin = ""
                                        newPin = ""
                                        confirmPin = ""
                                        "PIN updated"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Update PIN") }
                }
            }
        }
    }
}

@Composable
private fun AdminRowSwitch(
    adminGate: AdminGateState,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { value -> adminGate.withAdmin { onChecked(value) } },
            enabled = enabled
        )
    }
}
