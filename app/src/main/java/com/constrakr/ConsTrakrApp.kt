package com.constrakr

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.constrakr.liveness.MiniFasLivenessDetector
import com.constrakr.recognition.AdaFaceRecognizer
import com.constrakr.sync.SyncWorker
import com.constrakr.util.AppLog
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        scheduleBackgroundSync()
        registerDeviceIfSignedIn()
    }

    private fun registerDeviceIfSignedIn() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.syncCoordinator.registerDeviceWithServer()
                .onSuccess { AppLog.d("Startup device registration OK") }
                .onFailure { AppLog.w("Startup device registration skipped: ${it.message}") }
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

    companion object {
        lateinit var instance: ConsTrakrApp
            private set
    }
}
