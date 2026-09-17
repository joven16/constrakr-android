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
import com.constrakr.config.RegistrationPoseSettings
import com.constrakr.domain.FacePose
import com.constrakr.device.tracking.DeviceTrackingConfig
import com.constrakr.kiosk.AppPinSettings
import com.constrakr.kiosk.KioskController
import com.constrakr.ui.components.AdminGateState
import com.constrakr.ui.components.AppPinGateState
import com.constrakr.ui.components.ConsTrakrCard
import com.constrakr.ui.components.SyncPullToRefreshBox
import com.constrakr.ui.components.rememberAdminGate
import com.constrakr.ui.components.rememberAppPinGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onScanner: () -> Unit,
    onAdvanced: () -> Unit,
    onDeviceTracking: () -> Unit
) {
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
                        Text("Registration poses")
                    }
                    androidx.compose.material3.TextButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
                        Text("Advanced & diagnostics")
                    }
                    androidx.compose.material3.TextButton(onClick = onDeviceTracking, modifier = Modifier.fillMaxWidth()) {
                        Text("Device tracking (admin)")
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
    val poseLevel = remember(poseRev) { container.registrationPoseSettings.matchingLevel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registration settings") },
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
    val appPinGate = rememberAppPinGate(
        title = "App PIN required",
        subtitle = "Enter the 6-digit app PIN to change kiosk lock settings. Works offline."
    )
    val app = ConsTrakrApp.instance
    val kiosk = remember { KioskController(context) }
    val maintenanceActive by container.kioskMaintenanceSession.isActive.collectAsState()
    val settings = container.kioskSettings
    val appPin = container.appPinSettings
    val trackingConfig = container.deviceTrackingConfig

    var kioskEnabled by remember { mutableStateOf(settings.isKioskEnabled) }
    var autoBoot by remember { mutableStateOf(settings.autoStartOnBoot) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    var pinMessageIsError by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        appPin.ensureDefaultAppPinIfNeeded()
    }

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
            ConsTrakrCard {
                Text("Device tracking", style = MaterialTheme.typography.titleMedium)
                var trackingEnabled by remember { mutableStateOf(trackingConfig.isEnabled) }
                var normalInterval by remember { mutableStateOf(trackingConfig.normalIntervalMinutes) }
                var activeInterval by remember { mutableStateOf(trackingConfig.activeIntervalMinutes) }
                AdminRowSwitch(adminGate, "Enable device tracking", trackingEnabled) {
                    trackingEnabled = it
                    trackingConfig.isEnabled = it
                }
                Text("Normal interval", style = MaterialTheme.typography.bodySmall)
                RowIntervalChips(adminGate, normalInterval, listOf(15, 30)) {
                    normalInterval = it
                    trackingConfig.normalIntervalMinutes = it
                }
                Text("While charging / kiosk active", style = MaterialTheme.typography.bodySmall)
                RowIntervalChips(adminGate, activeInterval, listOf(15, 30)) {
                    activeInterval = it
                    trackingConfig.activeIntervalMinutes = it
                }
                Text(
                    "Reports kiosk hardware status to the server. Phone GPS is uploaded only when accuracy is within 5 m. " +
                        "Samsung Find remains the emergency recovery tool.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (kiosk.isDeviceOwner) {
                ConsTrakrCard {
                    Text("Device lock", style = MaterialTheme.typography.titleMedium)
                    AppPinRowSwitch(appPinGate, "Enabled", kioskEnabled) {
                        kioskEnabled = it
                        settings.isKioskEnabled = it
                        if (it && !maintenanceActive) {
                            kiosk.enterKioskIfNeeded(activity)
                        } else if (!it) {
                            kiosk.exitKioskForMaintenance(activity)
                        }
                    }
                    AppPinRowSwitch(appPinGate, "Auto-launch on boot", autoBoot) {
                        autoBoot = it
                        settings.autoStartOnBoot = it
                    }
                    if (maintenanceActive) {
                        OutlinedButton(
                            onClick = {
                                appPinGate.withAppPin {
                                    container.kioskMaintenanceSession.lock()
                                    kiosk.restoreKioskPolicies(activity)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Done") }
                    } else if (!kiosk.isInLockTask(activity) && kioskEnabled) {
                        Button(
                            onClick = { appPinGate.withAppPin { kiosk.enterKioskIfNeeded(activity) } },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enable now") }
                    }
                    Text(
                        "Kiosk enable/disable uses the app PIN and works offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                ConsTrakrCard {
                    Text("App PIN", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Stored on this device for kiosk exit and lock settings. Works offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        newPin,
                        { if (it.length <= AppPinSettings.PIN_LENGTH) newPin = it.filter { c -> c.isDigit() } },
                        label = { Text("New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        confirmPin,
                        { if (it.length <= AppPinSettings.PIN_LENGTH) confirmPin = it.filter { c -> c.isDigit() } },
                        label = { Text("Confirm new PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    pinMessage?.let {
                        Text(
                            it,
                            color = if (pinMessageIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Button(
                        onClick = {
                            adminGate.withAdmin {
                                when {
                                    newPin.length != AppPinSettings.PIN_LENGTH -> {
                                        pinMessageIsError = true
                                        pinMessage = "New PIN must be 6 digits"
                                    }
                                    newPin != confirmPin -> {
                                        pinMessageIsError = true
                                        pinMessage = "PINs do not match"
                                    }
                                    else -> {
                                        appPin.setAppPin(newPin)
                                        newPin = ""
                                        confirmPin = ""
                                        pinMessageIsError = false
                                        pinMessage = "App PIN updated"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = newPin.length == AppPinSettings.PIN_LENGTH &&
                            confirmPin.length == AppPinSettings.PIN_LENGTH
                    ) { Text("Update app PIN") }
                    Text(
                        "Requires admin code to change the app PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
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

@Composable
private fun RowIntervalChips(
    adminGate: AdminGateState,
    selected: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { minutes ->
            FilterChip(
                selected = selected == minutes,
                onClick = { adminGate.withAdmin { onSelect(minutes) } },
                label = { Text("${minutes} min") }
            )
        }
    }
}

@Composable
private fun AppPinRowSwitch(
    appPinGate: AppPinGateState,
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
            onCheckedChange = { value -> appPinGate.withAppPin { onChecked(value) } },
            enabled = enabled
        )
    }
}
