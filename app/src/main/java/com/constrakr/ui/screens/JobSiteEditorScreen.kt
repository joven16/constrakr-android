package com.constrakr.ui.screens

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.domain.JobSite
import com.constrakr.ui.components.rememberAdminGate
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun JobSiteEditorScreen(siteId: UUID?, onBack: () -> Unit, onSaved: () -> Unit) {
    val container = ConsTrakrApp.instance.container
    val existing = siteId?.let { container.jobSiteStore.site(it) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var location by remember { mutableStateOf(existing?.locationLabel ?: "") }
    var lat by remember { mutableStateOf(existing?.latitude?.toString() ?: "0") }
    var lon by remember { mutableStateOf(existing?.longitude?.toString() ?: "0") }
    var radius by remember { mutableStateOf((existing?.radiusMeters ?: JobSite.DEFAULT_RADIUS).toFloat()) }
    var isDefault by remember {
        mutableStateOf(existing?.id == container.jobSiteStore.defaultSiteId || existing == null)
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val adminGate = rememberAdminGate(forcePromptEachTime = true)

    fun commitSave() {
        val latitude = lat.toDoubleOrNull() ?: 0.0
        val longitude = lon.toDoubleOrNull() ?: 0.0
        require(name.isNotBlank()) { "Name required" }
        require(latitude != 0.0 || longitude != 0.0) { "Coordinates required" }
        val site = JobSite(
            id = existing?.id ?: UUID.randomUUID(),
            name = name.trim(),
            locationLabel = location.trim(),
            latitude = latitude,
            longitude = longitude,
            radiusMeters = JobSite.clampRadius(radius.toDouble()),
            updatedAtMillis = System.currentTimeMillis()
        )
        container.jobSiteStore.upsert(site)
        if (isDefault) container.jobSiteStore.setDefaultSite(site.id)
        onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (siteId == null) "Add job site" else "Edit job site") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Site name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(location, { location = it }, label = { Text("Location label") }, modifier = Modifier.fillMaxWidth())
            RowDefaultToggle(isDefault) { isDefault = it }
            Text("Radius: ${radius.toInt()} m")
            Slider(value = radius, onValueChange = { radius = it }, valueRange = 30f..100f, steps = 13)
            Button(
                onClick = {
                    scope.launch {
                        runCatching {
                            val client = LocationServices.getFusedLocationProviderClient(context)
                            val loc = client.getCurrentLocation(
                                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                CancellationTokenSource().token
                            ).await()
                            lat = loc.latitude.toString()
                            lon = loc.longitude.toString()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Use my current location") }
            OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") }, modifier = Modifier.fillMaxWidth())
            adminGate.blockedMessage?.let {
                Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { adminGate.withAdmin { commitSave() } },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text("Save") }
        }
    }
}

@Composable
private fun RowDefaultToggle(checked: Boolean, onChecked: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) { Text("Default for attendance checks") }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
