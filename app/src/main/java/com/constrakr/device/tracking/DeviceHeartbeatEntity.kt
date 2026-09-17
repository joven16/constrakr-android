package com.constrakr.device.tracking

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_heartbeats",
    indices = [Index(value = ["syncStatus", "timestampMillis"])]
)
data class DeviceHeartbeatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val siteId: String?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val networkType: String?,
    val isOnline: Boolean,
    val isKioskModeActive: Boolean,
    val deviceModel: String,
    val androidVersion: String,
    val appVersion: String,
    val timestampMillis: Long,
    val syncStatus: String = SYNC_PENDING,
    val syncedAtMillis: Long? = null
) {
    companion object {
        const val SYNC_PENDING = "pending"
        const val SYNC_SYNCED = "synced"
    }
}
