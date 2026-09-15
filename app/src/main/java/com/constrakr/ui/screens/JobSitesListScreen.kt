package com.constrakr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.domain.JobSite
import com.constrakr.ui.components.ConsTrakrCard
import com.constrakr.ui.components.SyncPullToRefreshBox
import com.constrakr.ui.components.rememberAdminGate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobSitesListScreen(
    onBack: () -> Unit,
    onEditSite: (UUID?) -> Unit
) {
    val container = ConsTrakrApp.instance.container
    val adminGate = rememberAdminGate(forcePromptEachTime = true)
    val revision by container.jobSiteStore.revision.collectAsState()
    val sites = remember(revision) { container.jobSiteStore.allSites }
    var geofenceEnabled by remember(revision) { mutableStateOf(container.geofenceSettings.isEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job Sites") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adminGate.withAdmin { onEditSite(null) } }) {
                Icon(Icons.Default.Add, contentDescription = "Add site")
            }
        }
    ) { padding ->
        SyncPullToRefreshBox(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    "Saving, deleting, or changing GPS rules requires the admin code each time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                adminGate.blockedMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                }
                ConsTrakrCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Require on-site GPS")
                            Text("Attendance must be inside default site radius")
                        }
                        Switch(
                            checked = geofenceEnabled,
                            onCheckedChange = { enabled ->
                                adminGate.withAdmin {
                                    if (enabled && !container.jobSiteStore.hasConfiguredSites) return@withAdmin
                                    container.geofenceSettings.isEnabled = enabled
                                    geofenceEnabled = enabled
                                }
                            }
                        )
                    }
                }
                if (container.jobSiteStore.pendingSyncCount > 0) {
                    Text("Pending sync: ${container.jobSiteStore.pendingSyncCount}", modifier = Modifier.padding(vertical = 8.dp))
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sites, key = { it.id }) { site ->
                        SiteRow(
                            site = site,
                            isDefault = container.jobSiteStore.defaultSiteId == site.id,
                            onClick = { adminGate.withAdmin { onEditSite(site.id) } },
                            onDelete = { adminGate.withAdmin { container.jobSiteStore.delete(site.id) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteRow(site: JobSite, isDefault: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    ConsTrakrCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(site.displayTitle)
                Text(site.locationLabel.ifEmpty { "No location label" })
                if (isDefault) Text("Default for attendance")
            }
            androidx.compose.material3.TextButton(onClick = onClick) { Text("Edit") }
            androidx.compose.material3.TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
