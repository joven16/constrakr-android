package com.constrakr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.domain.JobSite
import com.constrakr.ui.components.EmptyState
import com.constrakr.ui.components.rememberAdminGate
import com.constrakr.ui.components.SyncPullToRefreshBox
import com.constrakr.ui.theme.TealPrimary
import com.constrakr.ui.theme.WarningOrange
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
                },
                actions = {
                    IconButton(onClick = { adminGate.withAdmin { onEditSite(null) } }) {
                        Icon(Icons.Default.Add, contentDescription = "Add site")
                    }
                }
            )
        }
    ) { padding ->
        SyncPullToRefreshBox(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    JobSitesSection(title = null, footer = if (adminGate.blockedMessage != null) {
                        adminGate.blockedMessage
                    } else {
                        "When on, the scanner tab is blocked until the phone is at the default job site. Open a site below, turn on Default for attendance checks, then Save. Updating sites always requires the admin code."
                    }) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Require on-site GPS",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "Attendance must be inside default site radius",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                }

                if (container.jobSiteStore.pendingSyncCount > 0) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = WarningOrange.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = WarningOrange)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "${container.jobSiteStore.pendingSyncCount} job site change(s) waiting to sync",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "Pull down to sync now, or they'll upload automatically when you're back online.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (sites.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No Job Sites",
                            message = "Add a site with a name, map pin, and radius. Then set it as the default for attendance checks.",
                            action = "Add job site",
                            onAction = { adminGate.withAdmin { onEditSite(null) } }
                        )
                    }
                } else {
                    item {
                        JobSitesSection(
                            title = "Sites",
                            footer = "Select a site to edit it. Set Default for attendance checks, then Save. Saving or deleting requires the admin code."
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                sites.forEachIndexed { index, site ->
                                    JobSiteRow(
                                        site = site,
                                        isDefault = container.jobSiteStore.defaultSiteId == site.id,
                                        onClick = { onEditSite(site.id) }
                                    )
                                    if (index < sites.lastIndex) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        ) {
                                            Text("", modifier = Modifier.fillMaxWidth().padding(top = 0.5.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobSitesSection(
    title: String?,
    footer: String?,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(
                it.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                content()
            }
        }
        footer?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.contains("blocked", ignoreCase = true) || it.contains("admin code", ignoreCase = true) && adminGateErrorColor(it)) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

private fun adminGateErrorColor(text: String): Boolean =
    text.contains("blocked", ignoreCase = true) ||
        text.contains("No user assigned", ignoreCase = true) ||
        text.contains("admin code set", ignoreCase = true)

@Composable
private fun JobSiteRow(site: JobSite, isDefault: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = TealPrimary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(site.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (isDefault) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = TealPrimary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "Default",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = TealPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Text(
                site.displaySubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isDefault) {
                Text(
                    "Used for attendance GPS checks",
                    style = MaterialTheme.typography.bodySmall,
                    color = TealPrimary
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
