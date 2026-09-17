package com.constrakr.device.tracking

import android.content.Context
import com.constrakr.network.ApiClient
import com.constrakr.network.ConsTrakrApi
import com.constrakr.network.DeviceHeartbeatRequest
import com.constrakr.sync.SyncCoordinator
import com.constrakr.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class DeviceTrackingRepository(
    private val context: Context,
    private val dao: DeviceHeartbeatDao,
    private val config: DeviceTrackingConfig,
    private val collector: DeviceTrackingService,
    private val metadata: DeviceTrackingMetadataStore,
    private val api: ConsTrakrApi,
    private val syncCoordinator: SyncCoordinator
) {
    suspend fun collectAndQueueIfDue(force: Boolean = false): Result<Long?> = withContext(Dispatchers.IO) {
        if (!config.isEnabled) return@withContext Result.success(null)
        val snapshot = collector.collectSnapshot()
        val now = snapshot.request.timestamp
        metadata.lastCollectMillis = now
        if (snapshot.hadLocation) {
            metadata.lastLocationMillis = now
            metadata.lastLocationLat = snapshot.request.latitude
            metadata.lastLocationLng = snapshot.request.longitude
            metadata.lastLocationAccuracy = snapshot.request.accuracyMeters
        }

        if (!force && shouldSkipDuplicate(now, snapshot.request.isCharging, snapshot.request.isKioskModeActive)) {
            return@withContext Result.success(null)
        }

        val entity = snapshot.request.toEntity()
        runCatching { dao.insert(entity) }
            .onFailure { AppLog.w("Heartbeat queue skipped (duplicate window)") }
            .map { it }
    }

    suspend fun syncPending(): Result<Int> = withContext(Dispatchers.IO) {
        if (!syncCoordinator.isSignedIn) return@withContext Result.success(0)
        val token = syncCoordinator.authToken ?: return@withContext Result.success(0)
        val auth = ApiClient.authHeader(token) ?: return@withContext Result.success(0)
        val deviceId = syncCoordinator.deviceLocalId

        val pending = dao.pending()
        if (pending.isEmpty()) return@withContext Result.success(0)

        var synced = 0
        for (row in pending) {
            val payload = row.toRequest()
            runCatching {
                api.postDeviceHeartbeat(auth, deviceId, payload)
            }.onSuccess {
                val at = System.currentTimeMillis()
                dao.markSynced(row.id, DeviceHeartbeatEntity.SYNC_SYNCED, at)
                metadata.lastSuccessfulSyncMillis = at
                synced++
            }.onFailure { error ->
                AppLog.w("Heartbeat sync failed: ${error.message}")
                if (error is HttpException && error.code() == 401) {
                    syncCoordinator.markSessionExpired()
                }
                return@withContext Result.failure(error)
            }
        }
        pruneOldSynced()
        Result.success(synced)
    }

    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) {
        dao.pendingCount()
    }

    suspend fun latestLocal(): DeviceHeartbeatEntity? = withContext(Dispatchers.IO) {
        dao.latest()
    }

    private suspend fun shouldSkipDuplicate(now: Long, charging: Boolean, kioskActive: Boolean): Boolean {
        val minGap = config.intervalFor(charging, kioskActive)
        val latest = dao.latest() ?: return false
        return now - latest.timestampMillis < minGap
    }

    private suspend fun pruneOldSynced() {
        val cutoff = System.currentTimeMillis() - PRUNE_SYNCED_OLDER_THAN_MS
        dao.pruneSyncedBefore(DeviceHeartbeatEntity.SYNC_SYNCED, cutoff)
    }

    companion object {
        private const val PRUNE_SYNCED_OLDER_THAN_MS = 7L * 24 * 60 * 60 * 1000
    }
}

internal fun DeviceHeartbeatRequest.toEntity() = DeviceHeartbeatEntity(
    deviceId = deviceId,
    siteId = siteId,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    batteryPercent = batteryPercent,
    isCharging = isCharging,
    networkType = networkType,
    isOnline = isOnline,
    isKioskModeActive = isKioskModeActive,
    deviceModel = deviceModel,
    androidVersion = androidVersion,
    appVersion = appVersion,
    timestampMillis = timestamp
)

internal fun DeviceHeartbeatEntity.toRequest() = DeviceHeartbeatRequest(
    deviceId = deviceId,
    siteId = siteId,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    batteryPercent = batteryPercent,
    isCharging = isCharging,
    networkType = networkType,
    isOnline = isOnline,
    isKioskModeActive = isKioskModeActive,
    deviceModel = deviceModel,
    androidVersion = androidVersion,
    appVersion = appVersion,
    timestamp = timestampMillis
)

/** Pure helper for unit tests. */
fun shouldSkipHeartbeatDuplicate(lastTimestampMillis: Long?, nowMillis: Long, minGapMillis: Long): Boolean {
    if (lastTimestampMillis == null) return false
    return nowMillis - lastTimestampMillis < minGapMillis
}
