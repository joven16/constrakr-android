package com.constrakr.device.tracking

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DeviceTrackingScheduler {
    private const val WORK_NAME = "constrakr-device-heartbeat"

    fun schedule(context: Context, config: DeviceTrackingConfig) {
        val minutes = config.normalIntervalMinutes
            .coerceAtLeast(DeviceTrackingConfig.MIN_INTERVAL_MINUTES)
            .toLong()
        if (!config.isEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DeviceHeartbeatWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
