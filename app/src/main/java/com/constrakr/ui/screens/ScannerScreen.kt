package com.constrakr.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.constrakr.camera.CameraXManager
import com.constrakr.camera.toCameraFrame
import com.constrakr.domain.CheckType
import com.constrakr.ui.components.CameraPermissionGate
import com.constrakr.ui.components.ConnectivityChipVariant
import com.constrakr.ui.components.ConnectivityStatusChip
import com.constrakr.ui.components.FaceGuideOverlay
import com.constrakr.ui.components.ScannerBorderState
import com.constrakr.ui.components.VoicePrompt
import com.constrakr.ui.theme.SuccessGreen
import com.constrakr.ui.theme.TealPrimary
import com.constrakr.ui.theme.WarningOrange
import com.constrakr.viewmodel.ScannerViewModel
import com.google.mlkit.vision.common.InputImage

@Composable
fun ScannerScreen(
    onHiddenMaintenanceTap: () -> Unit = {},
    onLockScreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vm: ScannerViewModel = viewModel()
    val status by vm.status.collectAsState()
    val isActive by vm.isSessionActive.collectAsState()
    val borderState by vm.borderState.collectAsState()
    val locationBlocked by vm.locationBlocked.collectAsState()
    val locationMessage by vm.locationMessage.collectAsState()

    val voice = remember { VoicePrompt(context) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }

    LaunchedEffect(Unit) { vm.refreshLocationGate() }
    LaunchedEffect(Unit) {
        vm.speakEvent.collect { text -> voice.speakScanner(text) }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onLocationPermissionGranted() }

    var cameraGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { cameraGranted = it }

    CameraPermissionGate(
        denied = !cameraGranted,
        onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val previewView = remember { PreviewView(context) }
                DisposableEffect(lifecycleOwner) {
                    val camera = CameraXManager(context, lifecycleOwner)
                    camera.onFrame = frame@{ proxy ->
                        if (!vm.frameAnalysisEnabled) return@frame
                        val frame = proxy.toCameraFrame() ?: return@frame
                        vm.onFrame(frame.bitmap, frame.inputImage)
                    }
                    camera.bind(previewView)
                    onDispose { camera.shutdown() }
                }
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                FaceGuideOverlay(
                    caption = status,
                    conditionMet = borderState == ScannerBorderState.Success,
                    borderState = borderState,
                    ovalVerticalBias = 0.28f,
                    captionBelowOval = true,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { onHiddenMaintenanceTap() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.height(16.dp))
                        Text(
                            vm.operatingSiteLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectivityStatusChip(compact = true, variant = ConnectivityChipVariant.OnDark)
                        IconButton(onClick = onLockScreen) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock screen", tint = Color.White)
                        }
                    }
                }

                locationMessage?.let { msg ->
                    Text(
                        msg,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isActive) {
                    if (vm.needsLocationPermission) {
                        Button(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                        ) { Text("Payagan ang location") }
                    } else if (locationBlocked) {
                        OutlinedButton(
                            onClick = { vm.refreshLocationGate() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null)
                                Text("Recheck site")
                            }
                        }
                    } else {
                        PunchButton(
                            label = "Time In",
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        ) { vm.startPunch(CheckType.CHECK_IN) }
                        PunchButton(
                            label = "Time Out",
                            color = WarningOrange,
                            modifier = Modifier.weight(1f)
                        ) { vm.startPunch(CheckType.CHECK_OUT) }
                    }
                } else {
                    OutlinedButton(
                        onClick = { vm.cancelSession() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun PunchButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.85f)
        )
    ) { Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
}
