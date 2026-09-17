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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.kiosk.KioskController
import com.constrakr.ui.components.ConsTrakrCard
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceTrackingDiagnosticsScreen(onBack: () -> Unit) {
    val container = ConsTrakrApp.instance.container
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val kiosk = remember { KioskController(context) }
    val meta = container.deviceTrackingMetadata
    val online by container.networkMonitor.isOnline.collectAsState()
    val maintenanceActive by container.kioskMaintenanceSession.isActive.collectAsState()
    val scope = rememberCoroutineScope()

    var pendingCount by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }

    suspend fun refreshPending() {
        pendingCount = container.deviceTrackingRepository.pendingCount()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        refreshPending()
    }

    fun formatTime(millis: Long?): String {
        if (millis == null || millis <= 0L) return "—"
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(millis))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device tracking") },
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
            Text(
                "Admin only — company kiosk hardware monitoring. Not shown to workers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ConsTrakrCard {
                Text("Location (admin)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Only GPS fixes within 5 m accuracy are uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val lat = meta.lastLocationLat
                val lng = meta.lastLocationLng
                Text(
                    if (lat != null && lng != null) {
                        "Lat ${"%.5f".format(lat)}, Lng ${"%.5f".format(lng)}"
                    } else {
                        "No location captured yet"
                    }
                )
                meta.lastLocationAccuracy?.let {
                    Text("Accuracy: ${it.toInt()} m", style = MaterialTheme.typography.bodySmall)
                }
                Text("Last location update: ${formatTime(meta.lastLocationMillis)}")
            }
            ConsTrakrCard {
                Text("Sync", style = MaterialTheme.typography.titleMedium)
                Text("Last collect: ${formatTime(meta.lastCollectMillis)}")
                Text("Last server heartbeat: ${formatTime(meta.lastSuccessfulSyncMillis)}")
                Text("Pending queue: $pendingCount")
            }
            ConsTrakrCard {
                Text("Device state", style = MaterialTheme.typography.titleMedium)
                Text("Network: ${if (online) "Online" else "Offline"}")
                Text("Kiosk enabled: ${container.kioskSettings.isKioskEnabled}")
                Text("Lock task active: ${kiosk.isInLockTask(activity)}")
                Text("Maintenance window: ${if (maintenanceActive) "Active" else "Off"}")
                Text("Tracking enabled: ${container.deviceTrackingConfig.isEnabled}")
                Text(
                    "Interval: ${container.deviceTrackingConfig.normalIntervalMinutes} min " +
                        "(active ${container.deviceTrackingConfig.activeIntervalMinutes} min)"
                )
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(
                onClick = {
                    scope.launch {
                        container.deviceTrackingRepository.collectAndQueueIfDue(force = true)
                            .onSuccess {
                                container.deviceTrackingRepository.syncPending()
                                    .onSuccess { count ->
                                        status = if (count > 0) "Synced $count heartbeat(s)" else "Queued locally"
                                    }
                                    .onFailure { status = it.message ?: "Sync failed" }
                            }
                            .onFailure { status = it.message ?: "Collect failed" }
                        refreshPending()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Collect & sync now") }
        }
    }
}
