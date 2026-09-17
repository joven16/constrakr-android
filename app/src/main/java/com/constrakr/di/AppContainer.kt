package com.constrakr.di

import android.content.Context
import com.constrakr.ConsTrakrApp
import com.constrakr.admin.AdminCodeService
import com.constrakr.admin.AppAccessSession
import com.constrakr.attendance.AttendanceVerificationEngine
import com.constrakr.attendance.ClockIntegrityGuard
import com.constrakr.attendance.SiteLocationGate
import com.constrakr.config.AppThemeSettings
import com.constrakr.config.FaceScanSettings
import com.constrakr.config.RegistrationPoseSettings
import com.constrakr.database.AttendanceRepository
import com.constrakr.database.ConsTrakrDatabase
import com.constrakr.database.EmployeeRepository
import com.constrakr.domain.DeviceStore
import com.constrakr.domain.JobSiteStore
import com.constrakr.domain.SiteGeofenceSettings
import com.constrakr.device.tracking.DeviceTrackingConfig
import com.constrakr.device.tracking.DeviceTrackingMetadataStore
import com.constrakr.device.tracking.DeviceTrackingRepository
import com.constrakr.device.tracking.DeviceTrackingService
import com.constrakr.enrollment.EnrollmentEngine
import com.constrakr.kiosk.AppPinSettings
import com.constrakr.kiosk.KioskController
import com.constrakr.kiosk.KioskMaintenanceSession
import com.constrakr.kiosk.KioskSettings
import com.constrakr.face.FaceDetectionService
import com.constrakr.liveness.LivenessChallengeManager
import com.constrakr.network.ApiClient
import com.constrakr.recognition.FaceMatchingService
import com.constrakr.security.SecureFaceTemplateStore
import com.constrakr.sync.SyncCoordinator
import com.constrakr.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val app = context.applicationContext as ConsTrakrApp

    /** Survives tab switches — use for sign-in / sync started from More. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val networkMonitor = NetworkMonitor(context.applicationContext).also { it.start() }
    val database = ConsTrakrDatabase.build(context)
    val secureStore = SecureFaceTemplateStore(context)
    val jobSiteStore = JobSiteStore(context)
    val deviceStore = DeviceStore(context)
    val geofenceSettings = SiteGeofenceSettings(context, jobSiteStore)
    val appPinSettings = AppPinSettings(context)
    val kioskSettings = KioskSettings(context)
    val kioskMaintenanceSession = KioskMaintenanceSession(context)
    val accessSession = AppAccessSession(jobSiteStore, deviceStore)
    val themeSettings = AppThemeSettings(context)
    val registrationPoseSettings = RegistrationPoseSettings(context)
    val faceScanSettings = FaceScanSettings(context)
    val employeeRepository = EmployeeRepository(database, secureStore, context.applicationContext)
    val attendanceRepository = AttendanceRepository(database)
    val api = ApiClient.create(context)
    val clockGuard = ClockIntegrityGuard(context) {
        ClockIntegrityGuard.parseServerTimeMillis(
            runCatching { api.health().serverTime }.getOrNull()
        )
    }
    val locationGate = SiteLocationGate(context, jobSiteStore, geofenceSettings)
    val syncCoordinator = SyncCoordinator(
        context, api, employeeRepository, attendanceRepository, jobSiteStore, deviceStore
    )
    val deviceTrackingConfig = DeviceTrackingConfig(context)
    val deviceTrackingMetadata = DeviceTrackingMetadataStore(context)
    private val kioskControllerRef = KioskController(context)
    val deviceTrackingService = DeviceTrackingService(
        context.applicationContext,
        deviceStore,
        jobSiteStore,
        networkMonitor,
        kioskSettings,
        kioskMaintenanceSession,
        kioskControllerRef
    )
    val deviceTrackingRepository = DeviceTrackingRepository(
        context.applicationContext,
        database.deviceHeartbeatDao(),
        deviceTrackingConfig,
        deviceTrackingService,
        deviceTrackingMetadata,
        api,
        syncCoordinator
    )
    val adminCodeService = AdminCodeService(api, deviceStore, accessSession, syncCoordinator)
    val faceDetection = FaceDetectionService()
    val matchingService = FaceMatchingService()
    val verificationEngine = AttendanceVerificationEngine(matchingService)
    val livenessManager = LivenessChallengeManager()
    val enrollmentEngine = EnrollmentEngine(
        adaFace = app.adaFaceRecognizer,
        miniFas = app.miniFasDetector,
        faceDetection = faceDetection,
        secureStore = secureStore,
        employeeRepository = employeeRepository,
        poseSettings = registrationPoseSettings,
        syncCoordinator = syncCoordinator
    )
}
