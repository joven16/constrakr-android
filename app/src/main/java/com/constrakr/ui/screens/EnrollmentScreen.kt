package com.constrakr.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.constrakr.ConsTrakrApp
import com.constrakr.camera.CameraXManager
import com.constrakr.camera.toBitmap
import com.constrakr.domain.FacePose
import com.constrakr.enrollment.EnrollmentScanPhase
import com.constrakr.ui.components.CameraPermissionGate
import com.constrakr.ui.components.ConsTrakrCard
import com.constrakr.ui.components.FaceGuideOverlay
import com.constrakr.ui.components.ScannerBorderState
import com.constrakr.ui.components.VoicePrompt
import com.constrakr.ui.theme.SuccessGreen
import com.constrakr.ui.theme.TealPrimary
import com.constrakr.ui.util.toTitleCaseWords
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RegistrationStep(val title: String) {
    DETAILS("Details"),
    ID_DOCUMENT("Scan ID"),
    PROFILE_PHOTO("Profile photo"),
    FACE_SCAN("Face setup")
}

private enum class IdDocumentType(val raw: String, val label: String) {
    PHILSYS("philsys_national_id", "PhilSys National ID"),
    DRIVERS("drivers_license", "Driver's License"),
    PASSPORT("passport", "Passport"),
    SSS("sss_umid", "SSS / UMID"),
    VOTERS("voters_id", "Voter's ID"),
    OTHERS("others", "Other ID")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollmentScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val container = ConsTrakrApp.instance.container
    val engine = remember { container.enrollmentEngine }
    val uiState by engine.uiState.collectAsState()
    val poseRev by container.registrationPoseSettings.revision.collectAsState()
    val poseOrder = remember(poseRev) { container.registrationPoseSettings.enabledEnrollmentOrder() }

    var step by rememberSaveable { mutableIntStateOf(RegistrationStep.DETAILS.ordinal) }
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var dept by rememberSaveable { mutableStateOf("Construction") }
    var position by rememberSaveable { mutableStateOf("Worker") }
    var idType by rememberSaveable { mutableStateOf(IdDocumentType.PHILSYS.name) }
    var idNumber by rememberSaveable { mutableStateOf("") }
    var idExpanded by remember { mutableStateOf(false) }

    var cameraGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { cameraGranted = it }

    val currentStep = RegistrationStep.entries[step]
    val modelsReady = ConsTrakrApp.instance.adaFaceRecognizer.isReady
    val detailsValid = first.isNotBlank() && last.isNotBlank()
    val selectedIdType = IdDocumentType.entries.first { it.name == idType }

    var profileCaptured by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { engine.reset() }

    var autoSaved by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete && !autoSaved && !uiState.isSaving && detailsValid) {
            autoSaved = true
            val siteId = container.accessSession.operatorSiteId
            val site = siteId?.let { container.jobSiteStore.site(it) }
            val saved = engine.saveEmployee(
                first, last, dept, position,
                assignedSiteId = siteId,
                assignedSiteName = site?.displayTitle ?: "",
                assignedSiteLocation = site?.locationLabel ?: ""
            )
            saved.onSuccess { employee ->
                scope.launch(Dispatchers.IO) {
                    container.syncCoordinator.syncRegistration(employee.id)
                }
                delay(2_500L)
                onDone()
            }.onFailure {
                autoSaved = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("${currentStep.title} · ${step + 1}/4", style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (currentStep) {
                            RegistrationStep.DETAILS -> onDone()
                            RegistrationStep.ID_DOCUMENT -> step = RegistrationStep.DETAILS.ordinal
                            RegistrationStep.PROFILE_PHOTO -> step = RegistrationStep.ID_DOCUMENT.ordinal
                            RegistrationStep.FACE_SCAN -> step = RegistrationStep.PROFILE_PHOTO.ordinal
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.background, TealPrimary.copy(0.06f))
                    )
                )
        ) {
            when (currentStep) {
                RegistrationStep.DETAILS -> DetailsStep(
                    first = first,
                    onFirst = { first = it },
                    last = last,
                    onLast = { last = it },
                    dept = dept,
                    onDept = { dept = it },
                    position = position,
                    onPosition = { position = it },
                    siteTitle = container.accessSession.operatorSiteTitle,
                    onContinue = { if (detailsValid) step = RegistrationStep.ID_DOCUMENT.ordinal }
                )

                RegistrationStep.ID_DOCUMENT -> IdDocumentStep(
                    idType = selectedIdType,
                    idExpanded = idExpanded,
                    onExpanded = { idExpanded = it },
                    onIdType = { idType = it.name; idExpanded = false },
                    idNumber = idNumber,
                    onIdNumber = { idNumber = it },
                    onSkip = { step = RegistrationStep.PROFILE_PHOTO.ordinal },
                    onContinue = { step = RegistrationStep.PROFILE_PHOTO.ordinal }
                )

                RegistrationStep.PROFILE_PHOTO -> CameraPermissionGate(
                    denied = !cameraGranted,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                ) {
                    ProfilePhotoStep(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        engine = engine,
                        hasPhoto = profileCaptured || engine.hasProfilePhoto,
                        onCaptured = { profileCaptured = true },
                        onRetake = { profileCaptured = false },
                        onContinue = {
                            if (profileCaptured || engine.hasProfilePhoto) {
                                step = RegistrationStep.FACE_SCAN.ordinal
                            }
                        }
                    )
                }

                RegistrationStep.FACE_SCAN -> CameraPermissionGate(
                    denied = !cameraGranted,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                ) {
                    FaceScanStep(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        engine = engine,
                        uiState = uiState,
                        poseOrder = poseOrder,
                        modelsReady = modelsReady,
                        onRestart = { engine.resetFaceScan() }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsStep(
    first: String,
    onFirst: (String) -> Unit,
    last: String,
    onLast: (String) -> Unit,
    dept: String,
    onDept: (String) -> Unit,
    position: String,
    onPosition: (String) -> Unit,
    siteTitle: String?,
    onContinue: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConsTrakrCard {
            OutlinedTextField(
                value = first,
                onValueChange = { onFirst(it.toTitleCaseWords()) },
                label = { Text("First name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = last,
                onValueChange = { onLast(it.toTitleCaseWords()) },
                label = { Text("Last name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = dept,
                onValueChange = { onDept(it.toTitleCaseWords()) },
                label = { Text("Department") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = position,
                onValueChange = { onPosition(it.toTitleCaseWords()) },
                label = { Text("Position") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Site: ${siteTitle ?: "None — sync job sites first"}",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = onContinue,
            enabled = first.isNotBlank() && last.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
        ) { Text("Continue") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdDocumentStep(
    idType: IdDocumentType,
    idExpanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onIdType: (IdDocumentType) -> Unit,
    idNumber: String,
    onIdNumber: (String) -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Optional — skip if no ID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ConsTrakrCard {
            ExposedDropdownMenuBox(expanded = idExpanded, onExpandedChange = onExpanded) {
                OutlinedTextField(
                    value = idType.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("ID type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = idExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = idExpanded, onDismissRequest = { onExpanded(false) }) {
                    IdDocumentType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = { onIdType(type) }
                        )
                    }
                }
            }
            OutlinedTextField(
                idNumber,
                onIdNumber,
                label = { Text("ID number (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip") }
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) { Text("Continue") }
        }
    }
}

@Composable
private fun ProfilePhotoStep(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    engine: com.constrakr.enrollment.EnrollmentEngine,
    hasPhoto: Boolean,
    onCaptured: () -> Unit,
    onRetake: () -> Unit,
    onContinue: () -> Unit
) {
    val previewView = remember { PreviewView(context) }
    var previewReady by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val photoSaved = hasPhoto || previewBitmap != null

    Column(
        Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Profile photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Take a clear photo for IMS and employee records. Face scan comes in the next step.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (photoSaved) "Photo saved — retake if needed" else "Tap Capture when ready",
            style = MaterialTheme.typography.bodySmall,
            color = if (photoSaved) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 2.dp,
                    color = if (photoSaved) SuccessGreen else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = "Profile photo preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                DisposableEffect(Unit) {
                    previewReady = false
                    val camera = CameraXManager(context, lifecycleOwner)
                    camera.bind(previewView)
                    previewView.postDelayed({ previewReady = true }, 600L)
                    onDispose {
                        camera.shutdown()
                        previewReady = false
                    }
                }
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            }
        }
        if (photoSaved) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        engine.clearProfilePhoto()
                        previewBitmap = null
                        onRetake()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Retake") }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) { Text("Continue") }
            }
        } else {
            Button(
                onClick = {
                    val bitmap = previewView.bitmap ?: return@Button
                    engine.captureProfilePhoto(bitmap)
                    previewBitmap = bitmap
                    onCaptured()
                },
                enabled = previewReady,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) { Text("Capture photo") }
        }
    }
}

@Composable
private fun FaceScanStep(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    engine: com.constrakr.enrollment.EnrollmentEngine,
    uiState: com.constrakr.enrollment.EnrollmentUiState,
    poseOrder: List<FacePose>,
    modelsReady: Boolean,
    onRestart: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scanTitle = when (uiState.phase) {
        EnrollmentScanPhase.BLINK -> "Blink check"
        EnrollmentScanPhase.LIVE_CHECK -> "Live face scan"
        EnrollmentScanPhase.POSES -> uiState.currentPose.displayName
    }
    val overlayCaption = when {
        uiState.isComplete -> "Registration complete"
        else -> uiState.instruction
    }
    val overlayBorder = when {
        uiState.isComplete -> ScannerBorderState.Success
        uiState.error != null -> ScannerBorderState.Error
        uiState.guideMet -> ScannerBorderState.Active
        else -> ScannerBorderState.Ready
    }

    val voice = remember { VoicePrompt(context) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }
    LaunchedEffect(uiState.phase, uiState.currentPose, uiState.isComplete, uiState.error) {
        val speech = when {
            uiState.isComplete -> "Registration complete"
            uiState.error != null -> uiState.error
            uiState.phase == EnrollmentScanPhase.BLINK -> "Blink both eyes slowly"
            uiState.phase == EnrollmentScanPhase.LIVE_CHECK -> "Move closer to the camera"
            uiState.phase == EnrollmentScanPhase.POSES -> uiState.currentPose.displayName
            else -> null
        }
        if (!speech.isNullOrBlank()) {
            voice.speakEnrollment(speech)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhaseChip(
                label = "Blink",
                done = uiState.blinkPassed,
                active = uiState.phase == EnrollmentScanPhase.BLINK
            )
            PhaseChip(
                icon = Icons.Default.ViewInAr,
                done = uiState.liveCheckPassed,
                active = uiState.phase == EnrollmentScanPhase.LIVE_CHECK,
                contentDescription = "Live scan"
            )
            poseOrder.forEach { pose ->
                PhaseChip(
                    pose = pose,
                    done = uiState.capturedPoses.contains(pose),
                    active = uiState.phase == EnrollmentScanPhase.POSES &&
                        uiState.currentPose == pose &&
                        !uiState.isComplete
                )
            }
        }

        Text(scanTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            overlayCaption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )

        LinearProgressIndicator(
            progress = {
                when {
                    !uiState.blinkPassed -> 0.08f
                    !uiState.liveCheckPassed -> 0.12f + uiState.liveCheckProgress * 0.28f
                    else -> 0.4f + uiState.progress * 0.6f
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            color = TealPrimary
        )

        uiState.error?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (!modelsReady) {
            Text("Face model missing.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            val previewView = remember { PreviewView(context) }
            DisposableEffect(Unit) {
                engine.resetFaceScan()
                val camera = CameraXManager(context, lifecycleOwner)
                camera.onFrame = frame@{ proxy ->
                    val bitmap = proxy.toBitmap() ?: return@frame
                    val image = InputImage.fromBitmap(bitmap, 0)
                    scope.launch { engine.processFrame(bitmap, image) }
                }
                camera.bind(previewView)
                onDispose { camera.shutdown() }
            }
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            FaceGuideOverlay(
                caption = overlayCaption,
                conditionMet = uiState.guideMet || uiState.isComplete,
                borderState = overlayBorder,
                enrollmentPose = if (uiState.phase == EnrollmentScanPhase.POSES) uiState.currentPose else null,
                ovalVerticalBias = 0.28f,
                captionBelowOval = true,
                modifier = Modifier.fillMaxSize()
            )
        }

        OutlinedButton(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = !uiState.isSaving
        ) { Text("Restart") }
    }
}

@Composable
private fun PhaseChip(label: String, done: Boolean, active: Boolean) {
    PhaseChip(
        icon = Icons.Default.RemoveRedEye,
        done = done,
        active = active,
        contentDescription = label
    )
}

@Composable
private fun PhaseChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    done: Boolean,
    active: Boolean,
    contentDescription: String
) {
    PhaseChipIcon(icon = icon, done = done, active = active, contentDescription = contentDescription)
}

@Composable
private fun PhaseChip(pose: FacePose, done: Boolean, active: Boolean) {
    val icon = when (pose) {
        FacePose.CENTER -> Icons.Default.Face
        FacePose.LEFT -> Icons.AutoMirrored.Filled.ArrowBack
        FacePose.RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
        FacePose.UP -> Icons.Default.ArrowUpward
        FacePose.DOWN -> Icons.Default.ArrowDownward
    }
    PhaseChipIcon(icon = icon, done = done, active = active, contentDescription = pose.displayName)
}

@Composable
private fun PhaseChipIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    done: Boolean,
    active: Boolean,
    contentDescription: String
) {
    val borderColor = when {
        done -> SuccessGreen
        active -> TealPrimary
        else -> Color.Transparent
    }
    Surface(
        shape = CircleShape,
        color = when {
            done -> SuccessGreen
            active -> TealPrimary.copy(alpha = 0.18f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier
            .size(32.dp)
            .then(if (active && !done) Modifier.border(2.dp, borderColor, CircleShape) else Modifier)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (done) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            } else {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = if (active) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
