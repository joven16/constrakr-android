package com.constrakr.device.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.constrakr.ConsTrakrApp
import com.constrakr.util.AppLog
import java.io.IOException
import retrofit2.HttpException

class DeviceHeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ConsTrakrApp
        val repo = app.container.deviceTrackingRepository
        val config = app.container.deviceTrackingConfig
        if (!config.isEnabled) return Result.success()

        repo.collectAndQueueIfDue()
            .onFailure { AppLog.w("Device heartbeat collect failed: ${it.message}") }

        return repo.syncPending().fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                when (error) {
                    is IOException -> Result.retry()
                    is HttpException -> if (error.code() in 500..599) Result.retry() else Result.success()
                    else -> Result.retry()
                }
            }
        )
    }
}
