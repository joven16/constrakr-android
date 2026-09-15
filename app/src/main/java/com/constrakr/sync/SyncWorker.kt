package com.constrakr.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as com.constrakr.ConsTrakrApp
        return app.container.syncCoordinator.syncPending()
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
