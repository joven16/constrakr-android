package com.constrakr.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.constrakr.ConsTrakrApp
import com.constrakr.domain.FacePose
import com.constrakr.ui.components.EmployeePhotosPanel
import com.constrakr.ui.util.toTitleCaseWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeEditScreen(employeeId: UUID, onBack: () -> Unit, onSaved: () -> Unit) {
    val container = ConsTrakrApp.instance.container
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var dept by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var siteName by remember { mutableStateOf("") }
    var profileJpeg by remember { mutableStateOf<ByteArray?>(null) }
    var posePhotos by remember { mutableStateOf<Map<FacePose, ByteArray>>(emptyMap()) }
    var isEnrolled by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(employeeId) {
        withContext(Dispatchers.IO) { container.employeeRepository.getById(employeeId) }?.let { e ->
            first = e.firstName
            last = e.lastName
            dept = e.department
            position = e.position
            siteName = e.assignedSiteName
            isEnrolled = e.isEnrolled
        }
        withContext(Dispatchers.IO) {
            if (container.employeeRepository.getProfilePhoto(employeeId) == null) {
                container.syncCoordinator.ensureLocalProfilePhoto(employeeId)
            }
            container.syncCoordinator.ensureLocalEnrollmentPhotos(employeeId)
        }
        profileJpeg = withContext(Dispatchers.IO) {
            container.employeeRepository.getProfilePhoto(employeeId)
        }
        posePhotos = withContext(Dispatchers.IO) {
            container.employeeRepository.getEnrollmentPhotos(employeeId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit employee") },
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
                .verticalScroll(rememberScrollState())
        ) {
            EmployeePhotosPanel(
                profileJpeg = profileJpeg,
                posePhotos = posePhotos,
                showEnrollmentPoses = isEnrolled || posePhotos.isNotEmpty(),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            OutlinedTextField(
                value = first,
                onValueChange = { first = it.toTitleCaseWords() },
                label = { Text("First name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = last,
                onValueChange = { last = it.toTitleCaseWords() },
                label = { Text("Last name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = dept,
                onValueChange = { dept = it.toTitleCaseWords() },
                label = { Text("Department") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = position,
                onValueChange = { position = it.toTitleCaseWords() },
                label = { Text("Position") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(siteName, {}, label = { Text("Job site") }, modifier = Modifier.fillMaxWidth(), readOnly = true)
            error?.let {
                Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val e = container.employeeRepository.getById(employeeId)
                                    ?: error("Employee not found")
                                container.employeeRepository.updateProfile(
                                    id = employeeId,
                                    firstName = first,
                                    lastName = last,
                                    department = dept,
                                    position = position,
                                    assignedSiteId = e.assignedSiteId,
                                    assignedSiteName = e.assignedSiteName,
                                    assignedSiteLocation = e.assignedSiteLocation
                                )
                                if (container.syncCoordinator.isSignedIn) {
                                    container.syncCoordinator.syncPending().getOrThrow()
                                }
                            }
                        }
                        saving = false
                        result.onSuccess { onSaved() }.onFailure { error = it.message ?: "Save failed" }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text(if (saving) "Saving…" else "Save") }
        }
    }
}
