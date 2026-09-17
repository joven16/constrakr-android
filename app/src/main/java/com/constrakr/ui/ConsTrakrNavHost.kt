package com.constrakr.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.constrakr.ui.components.ConnectivityStatusChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.constrakr.ConsTrakrApp
import com.constrakr.MainActivity
import com.constrakr.kiosk.KioskController
import com.constrakr.ui.components.AppPinSheet
import com.constrakr.ui.screens.DashboardScreen
import com.constrakr.ui.screens.DtrScreen
import com.constrakr.ui.screens.EmployeeDetailScreen
import com.constrakr.ui.screens.EmployeeEditScreen
import com.constrakr.ui.screens.EmployeesScreen
import com.constrakr.ui.screens.EnrollmentScreen
import com.constrakr.ui.screens.JobSiteEditorScreen
import com.constrakr.ui.screens.JobSitesListScreen
import com.constrakr.ui.screens.MoreScreen
import com.constrakr.ui.screens.ScannerScreen
import com.constrakr.ui.screens.DeviceTrackingDiagnosticsScreen
import com.constrakr.ui.screens.SettingsAdvancedScreen
import com.constrakr.ui.screens.SettingsAppearanceScreen
import com.constrakr.ui.screens.SettingsHubScreen
import com.constrakr.ui.screens.SettingsScannerScreen
import com.constrakr.ui.theme.CyanAccent
import com.constrakr.ui.theme.TealPrimary
import java.util.UUID

enum class MainTab(val label: String) {
    Dashboard("Dashboard"),
    Employees("Employees"),
    Dtr("DTR"),
    Scanner("Scanner"),
    More("More")
}

private sealed class Overlay {
    data object None : Overlay()
    data object Enrollment : Overlay()
    data object JobSites : Overlay()
    data class JobSiteEditor(val id: UUID?) : Overlay()
    data object Settings : Overlay()
    data object SettingsScanner : Overlay()
    data object SettingsAdvanced : Overlay()
    data object DeviceTrackingDiagnostics : Overlay()
    data object SettingsAppearance : Overlay()
    data class EmployeeDetail(val id: UUID) : Overlay()
    data class EmployeeEdit(val id: UUID) : Overlay()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsTrakrNavHost() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val container = ConsTrakrApp.instance.container
    val kiosk = remember(activity) { KioskController(activity) }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    var logoTaps by rememberSaveable { mutableIntStateOf(0) }
    var showMaintenancePin by remember { mutableStateOf(false) }
    val maintenanceActive by container.kioskMaintenanceSession.isActive.collectAsState()

    val kioskController = (activity as? MainActivity)?.kioskController ?: kiosk

    LaunchedEffect(maintenanceActive) {
        if (!maintenanceActive) {
            kioskController.restoreKioskPolicies(activity)
            return@LaunchedEffect
        }
        while (container.kioskMaintenanceSession.isActive.value) {
            kotlinx.coroutines.delay(10_000)
            container.kioskMaintenanceSession.refresh()
        }
        kioskController.restoreKioskPolicies(activity)
    }

    LaunchedEffect(overlay, maintenanceActive) {
        if (maintenanceActive) return@LaunchedEffect
        kioskController.enterKioskIfNeeded(activity)
    }

    fun onHiddenMaintenanceTap() {
        logoTaps++
        if (logoTaps >= 7) {
            logoTaps = 0
            if (kiosk.isDeviceOwner) {
                showMaintenancePin = true
            }
        }
    }

    fun requestScreenLock() {
        kioskController.lockDeviceScreen()
    }

    BackHandler(enabled = showMaintenancePin) { showMaintenancePin = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AppPinSheet(
            visible = showMaintenancePin,
            onDismiss = { showMaintenancePin = false },
            onVerified = {
                container.kioskMaintenanceSession.unlock()
                kiosk.exitKioskForMaintenance(activity)
            },
            title = "Exit kiosk mode",
            subtitle = "Enter the 6-digit app PIN for temporary access (15 minutes). Works offline."
        )

        when (val o = overlay) {
            Overlay.Enrollment -> {
                BackHandler { overlay = Overlay.None }
                EnrollmentScreen(onDone = {
                    overlay = Overlay.None
                    tab = MainTab.Employees.ordinal
                })
            }
            Overlay.JobSites -> {
                BackHandler { overlay = Overlay.None }
                JobSitesListScreen(
                    onBack = { overlay = Overlay.None },
                    onEditSite = { overlay = Overlay.JobSiteEditor(it) }
                )
            }
            is Overlay.JobSiteEditor -> {
                BackHandler { overlay = Overlay.JobSites }
                JobSiteEditorScreen(
                    siteId = o.id,
                    onBack = { overlay = Overlay.JobSites },
                    onSaved = { overlay = Overlay.JobSites }
                )
            }
            Overlay.Settings -> {
                BackHandler { overlay = Overlay.None }
                SettingsHubScreen(
                    onBack = { overlay = Overlay.None },
                    onScanner = { overlay = Overlay.SettingsScanner },
                    onAdvanced = { overlay = Overlay.SettingsAdvanced },
                    onDeviceTracking = { overlay = Overlay.DeviceTrackingDiagnostics }
                )
            }
            Overlay.DeviceTrackingDiagnostics -> {
                BackHandler { overlay = Overlay.Settings }
                DeviceTrackingDiagnosticsScreen(onBack = { overlay = Overlay.Settings })
            }
            Overlay.SettingsScanner -> {
                BackHandler { overlay = Overlay.Settings }
                SettingsScannerScreen(onBack = { overlay = Overlay.Settings })
            }
            Overlay.SettingsAdvanced -> {
                BackHandler { overlay = Overlay.Settings }
                SettingsAdvancedScreen(onBack = { overlay = Overlay.Settings })
            }
            Overlay.SettingsAppearance -> {
                BackHandler { overlay = Overlay.None }
                SettingsAppearanceScreen(onBack = { overlay = Overlay.None })
            }
            is Overlay.EmployeeDetail -> {
                BackHandler { overlay = Overlay.None }
                EmployeeDetailScreen(
                    employeeId = o.id,
                    onBack = { overlay = Overlay.None },
                    onEdit = { overlay = Overlay.EmployeeEdit(it) }
                )
            }
            is Overlay.EmployeeEdit -> {
                BackHandler { overlay = Overlay.EmployeeDetail(o.id) }
                EmployeeEditScreen(
                    employeeId = o.id,
                    onBack = { overlay = Overlay.EmployeeDetail(o.id) },
                    onSaved = { overlay = Overlay.EmployeeDetail(o.id) }
                )
            }
            Overlay.None -> {
                val currentTab = MainTab.entries[tab]
                val hideTopBar = currentTab == MainTab.Scanner
                Scaffold(
            topBar = {
                if (!hideTopBar) {
                    TopAppBar(
                        title = {
                            Box(modifier = Modifier.clickable { onHiddenMaintenanceTap() }) {
                                Text("ConsTrakr", color = TealPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            }
                        },
                        actions = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ConnectivityStatusChip(compact = true)
                                IconButton(onClick = ::requestScreenLock) {
                                    Icon(Icons.Default.Lock, contentDescription = "Lock screen")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
                ) {
                    MainTab.entries.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyanAccent,
                                selectedTextColor = CyanAccent,
                                unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = CyanAccent.copy(alpha = 0.15f)
                            ),
                            icon = {
                                Icon(
                                    when (item) {
                                        MainTab.Dashboard -> Icons.Default.Home
                                        MainTab.Employees -> Icons.Default.Person
                                        MainTab.Dtr -> Icons.Default.DateRange
                                        MainTab.Scanner -> Icons.Default.Face
                                        MainTab.More -> Icons.Default.Menu
                                    },
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (MainTab.entries[tab]) {
                            MainTab.Dashboard -> DashboardScreen()
                            MainTab.Employees -> EmployeesScreen(
                                onRegister = { overlay = Overlay.Enrollment },
                                onOpenEmployee = { overlay = Overlay.EmployeeDetail(it) }
                            )
                            MainTab.Dtr -> DtrScreen()
                            MainTab.Scanner -> ScannerScreen(
                                onHiddenMaintenanceTap = ::onHiddenMaintenanceTap,
                                onLockScreen = ::requestScreenLock
                            )
                            MainTab.More -> MoreScreen(
                                maintenanceActive = maintenanceActive,
                                onEndMaintenance = {
                                    container.kioskMaintenanceSession.lock()
                                    kiosk.restoreKioskPolicies(activity)
                                },
                                onJobSites = { overlay = Overlay.JobSites },
                                onSettings = { overlay = Overlay.Settings },
                                onAppearance = { overlay = Overlay.SettingsAppearance }
                            )
                        }
                    }
                }
            }
        }
        if (overlay != Overlay.None) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .zIndex(20f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectivityStatusChip(compact = true)
                IconButton(onClick = ::requestScreenLock) {
                    Icon(Icons.Default.Lock, contentDescription = "Lock screen")
                }
            }
        }
    }
}
