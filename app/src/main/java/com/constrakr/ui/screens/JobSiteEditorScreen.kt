package com.constrakr.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.domain.JobSite
import com.constrakr.ui.components.JobSiteMapPinEditor
import com.constrakr.ui.components.rememberAdminGate
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.abs

private data class JobSiteFormSnapshot(
    val name: String,
    val locationLabel: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val isDefaultSite: Boolean
) {
    fun matches(
        name: String,
        locationLabel: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        isDefaultSite: Boolean
    ): Boolean {
        return name.trim() == this.name &&
            locationLabel.trim() == this.locationLabel &&
            abs(latitude - this.latitude) < 0.000001 &&
            abs(longitude - this.longitude) < 0.000001 &&
            radiusMeters == this.radiusMeters &&
            isDefaultSite == this.isDefaultSite
    }

    companion object {
        fun from(site: JobSite, isDefault: Boolean) = JobSiteFormSnapshot(
            name = site.name.trim(),
            locationLabel = site.locationLabel.trim(),
            latitude = site.latitude,
            longitude = site.longitude,
            radiusMeters = JobSite.clampRadius(site.radiusMeters),
            isDefaultSite = isDefault
        )

        val empty = JobSiteFormSnapshot("", "", 0.0, 0.0, JobSite.DEFAULT_RADIUS, false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun JobSiteEditorScreen(siteId: UUID?, onBack: () -> Unit, onSaved: () -> Unit) {
    val container = ConsTrakrApp.instance.container
    val existing = siteId?.let { container.jobSiteStore.site(it) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var location by remember { mutableStateOf(existing?.locationLabel ?: "") }
    var latitude by remember { mutableStateOf(existing?.latitude ?: 0.0) }
    var longitude by remember { mutableStateOf(existing?.longitude ?: 0.0) }
    var latitudeText by remember { mutableStateOf(formatCoordinate(existing?.latitude ?: 0.0)) }
    var longitudeText by remember { mutableStateOf(formatCoordinate(existing?.longitude ?: 0.0)) }
    var radius by remember { mutableStateOf((existing?.radiusMeters ?: JobSite.DEFAULT_RADIUS).toFloat()) }
    var isDefault by remember {
        mutableStateOf(existing?.id == container.jobSiteStore.defaultSiteId || existing == null && container.jobSiteStore.defaultSiteId == null)
    }
    var mapRecenterToken by remember { mutableIntStateOf(0) }
    var isCapturingLocation by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val initialSnapshot = remember(siteId) {
        existing?.let { JobSiteFormSnapshot.from(it, it.id == container.jobSiteStore.defaultSiteId) }
            ?: JobSiteFormSnapshot.empty.copy(isDefaultSite = container.jobSiteStore.defaultSiteId == null)
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val adminGate = rememberAdminGate(forcePromptEachTime = true)

    val formIsValid = name.trim().isNotEmpty() && (latitude != 0.0 || longitude != 0.0)
    val hasChanges = !initialSnapshot.matches(
        name = name,
        locationLabel = location,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = JobSite.clampRadius(radius.toDouble()),
        isDefaultSite = isDefault
    )
    val canSave = formIsValid && hasChanges

    fun syncCoordinateFieldsFromState() {
        if (latitude == 0.0 && longitude == 0.0) {
            latitudeText = ""
            longitudeText = ""
        } else {
            latitudeText = formatCoordinate(latitude)
            longitudeText = formatCoordinate(longitude)
        }
    }

    fun commitSave() {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Site name is required." }
        require(latitude != 0.0 || longitude != 0.0) { "Drop a pin on the map or use your current location." }
        val site = JobSite(
            id = existing?.id ?: UUID.randomUUID(),
            name = trimmedName,
            locationLabel = location.trim(),
            latitude = latitude,
            longitude = longitude,
            radiusMeters = JobSite.clampRadius(radius.toDouble()),
            updatedAtMillis = System.currentTimeMillis()
        )
        container.jobSiteStore.upsert(site)
        if (isDefault) {
            container.jobSiteStore.setDefaultSite(site.id)
        } else if (container.jobSiteStore.defaultSiteId == site.id) {
            container.jobSiteStore.setDefaultSite(null)
        }
        onSaved()
    }

    fun applyManualCoordinates() {
        val lat = latitudeText.trim().toDoubleOrNull()
        val lon = longitudeText.trim().toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            errorMessage = "Enter valid latitude (-90 to 90) and longitude (-180 to 180)."
            return
        }
        latitude = lat
        longitude = lon
        mapRecenterToken++
        errorMessage = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (siteId == null) "Add Site" else "Edit Site") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EditorSection(
                title = "Site details",
                footer = null
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Site name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (e.g. Makati HQ)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            EditorSection(
                title = "Default site",
                footer = "Select this site as the default used for Time In / Time Out GPS checks, then tap Save."
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Default for attendance checks", modifier = Modifier.weight(1f))
                    Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                }
            }

            EditorSection(
                title = "Map pin",
                footer = "Pan the map, use +/− to zoom, or change the layer for Standard, Satellite, or Hybrid. The red pin marks the site center and the teal circle shows the attendance radius."
            ) {
                JobSiteMapPinEditor(
                    latitude = latitude,
                    longitude = longitude,
                    onCoordinatesChanged = { lat, lon ->
                        latitude = lat
                        longitude = lon
                        syncCoordinateFieldsFromState()
                    },
                    radiusMeters = JobSite.clampRadius(radius.toDouble()),
                    recenterToken = mapRecenterToken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
                Text("Radius: ${radius.toInt()} m", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = JobSite.MIN_RADIUS.toFloat()..JobSite.MAX_RADIUS.toFloat(),
                    steps = 24
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isCapturingLocation = true
                            runCatching {
                                val client = LocationServices.getFusedLocationProviderClient(context)
                                val loc = client.getCurrentLocation(
                                    Priority.PRIORITY_HIGH_ACCURACY,
                                    CancellationTokenSource().token
                                ).await()
                                latitude = loc.latitude
                                longitude = loc.longitude
                                syncCoordinateFieldsFromState()
                                mapRecenterToken++
                                errorMessage = null
                            }.onFailure {
                                errorMessage = it.message ?: "Could not get current location."
                            }
                            isCapturingLocation = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCapturingLocation
                ) {
                    if (isCapturingLocation) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("Use My Current Location")
                }
            }

            EditorSection(
                title = "Manual coordinates",
                footer = "Enter latitude (-90 to 90) and longitude (-180 to 180), then apply to move the map pin."
            ) {
                OutlinedTextField(
                    value = latitudeText,
                    onValueChange = { latitudeText = it },
                    label = { Text("Latitude") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = longitudeText,
                    onValueChange = { longitudeText = it },
                    label = { Text("Longitude") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = ::applyManualCoordinates,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = latitudeText.isNotBlank() && longitudeText.isNotBlank()
                ) {
                    Text("Apply Coordinates")
                }
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            adminGate.blockedMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    adminGate.withAdmin {
                        runCatching { commitSave() }.onFailure { errorMessage = it.message }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }

            if (existing != null) {
                OutlinedButton(
                    onClick = {
                        adminGate.withAdmin {
                            container.jobSiteStore.delete(existing.id)
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Delete job site")
                }
            }
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    footer: String?,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
        footer?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

private fun formatCoordinate(value: Double): String {
    if (value == 0.0) return ""
    return String.format("%.6f", value)
}
