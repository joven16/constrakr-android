package com.constrakr

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.constrakr.liveness.MiniFasLivenessDetector
import com.constrakr.recognition.AdaFaceRecognizer
import com.constrakr.device.tracking.DeviceTrackingScheduler
import com.constrakr.sync.SyncWorker
import com.constrakr.util.AppLog
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ConsTrakrApp : Application() {
    lateinit var adaFaceRecognizer: AdaFaceRecognizer
        private set
    lateinit var miniFasDetector: MiniFasLivenessDetector
        private set
    lateinit var container: com.constrakr.di.AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.constrakr.config.MatchThresholdSettings.init(this)
        adaFaceRecognizer = AdaFaceRecognizer(this)
        miniFasDetector = MiniFasLivenessDetector(this)
        container = com.constrakr.di.AppContainer(this)
        migrateLegacyPosePhotos()
        scheduleBackgroundSync()
        scheduleDeviceTracking()
        registerDeviceIfSignedIn()
        startRemoteCommandPolling()
    }

    private fun migrateLegacyPosePhotos() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                com.constrakr.database.PosePhotoOrientationMigration.runIfNeeded(
                    this@ConsTrakrApp,
                    container.database
                )
            }.onFailure { AppLog.w("Pose photo orientation migration skipped: ${it.message}") }
        }
    }

    private fun registerDeviceIfSignedIn() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.syncCoordinator.registerDeviceWithServer()
                .onSuccess {
                    AppLog.d("Startup device registration OK")
                    container.deviceCommandService.pollAndExecute()
                }
                .onFailure { AppLog.w("Startup device registration skipped: ${it.message}") }
        }
    }

    private fun startRemoteCommandPolling() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (isActive) {
                container.deviceCommandService.pollAndExecute()
                delay(REMOTE_COMMAND_POLL_MS)
            }
        }
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "constrakr-sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleDeviceTracking() {
        DeviceTrackingScheduler.schedule(this, container.deviceTrackingConfig)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            container.deviceTrackingConfig.revision.collect {
                DeviceTrackingScheduler.schedule(this@ConsTrakrApp, container.deviceTrackingConfig)
            }
        }
    }

    companion object {
        private const val REMOTE_COMMAND_POLL_MS = 10_000L

        lateinit var instance: ConsTrakrApp
            private set
    }
}
