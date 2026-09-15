package com.constrakr.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.domain.Employee
import com.constrakr.ui.components.ConsTrakrCard
import com.constrakr.ui.components.rememberAdminGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDetailScreen(
    employeeId: UUID,
    onBack: () -> Unit,
    onEdit: (UUID) -> Unit
) {
    val container = ConsTrakrApp.instance.container
    val scope = rememberCoroutineScope()
    val adminGate = rememberAdminGate()
    var employee by remember { mutableStateOf<Employee?>(null) }
    var profileJpeg by remember { mutableStateOf<ByteArray?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(employeeId) {
        employee = withContext(Dispatchers.IO) { container.employeeRepository.getById(employeeId) }
        profileJpeg = withContext(Dispatchers.IO) {
            container.employeeRepository.getProfilePhoto(employeeId)
        }
    }

    if (showDeleteConfirm) {
        val e = employee
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete employee?") },
            text = {
                if (e != null) {
                    Text(
                        "${e.fullName} (${e.employeeCode}) will be removed from this device, " +
                            "including face enrollment data. If synced to the server, their record stays on the server " +
                            "as Removed from app (attendance history preserved)."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = employee ?: return@TextButton
                        showDeleteConfirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                container.syncCoordinator.deleteEmployee(toDelete)
                            }
                            onBack()
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(employee?.fullName ?: "Employee") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (employee != null && container.accessSession.canEditEmployee(employee!!)) {
                        IconButton(onClick = { onEdit(employeeId) }) {
                            Text("Edit")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val e = employee ?: return@Column
            val profileBitmap = remember(profileJpeg) {
                profileJpeg?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(120.dp)
            ) {
                if (profileBitmap != null) {
                    Image(
                        bitmap = profileBitmap.asImageBitmap(),
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ConsTrakrCard {
                Text("Code: ${e.employeeCode}")
                Text("Department: ${e.department}")
                Text("Position: ${e.position}")
                Text("Job site: ${e.assignedSiteName.ifEmpty { "Unassigned" }}")
                Text("Enrolled: ${if (e.isEnrolled) "Yes (${e.faceEmbeddings.size} poses)" else "No"}")
                Text("Sync: ${e.syncStatus.name.lowercase()}")
            }
            ConsTrakrCard {
                Text("Registered faces")
                e.faceEmbeddings.forEach { emb ->
                    Text("· ${emb.pose.displayName}")
                }
                if (e.faceEmbeddings.isEmpty()) Text("No embeddings stored.")
            }
            adminGate.blockedMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { adminGate.withAdmin { showDeleteConfirm = true } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete employee")
            }
        }
    }
}
