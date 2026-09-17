package com.constrakr.device.tracking

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeviceHeartbeatDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DeviceHeartbeatEntity): Long

    @Query(
        """SELECT * FROM device_heartbeats 
           WHERE syncStatus = :pending 
           ORDER BY timestampMillis ASC"""
    )
    suspend fun pending(pending: String = DeviceHeartbeatEntity.SYNC_PENDING): List<DeviceHeartbeatEntity>

    @Query("SELECT COUNT(*) FROM device_heartbeats WHERE syncStatus = :pending")
    suspend fun pendingCount(pending: String = DeviceHeartbeatEntity.SYNC_PENDING): Int

    @Query(
        """SELECT * FROM device_heartbeats 
           ORDER BY timestampMillis DESC LIMIT 1"""
    )
    suspend fun latest(): DeviceHeartbeatEntity?

    @Query(
        """SELECT * FROM device_heartbeats 
           WHERE syncStatus = :synced 
           ORDER BY syncedAtMillis DESC LIMIT 1"""
    )
    suspend fun latestSynced(synced: String = DeviceHeartbeatEntity.SYNC_SYNCED): DeviceHeartbeatEntity?

    @Query(
        """SELECT COUNT(*) FROM device_heartbeats 
           WHERE timestampMillis >= :sinceMillis"""
    )
    suspend fun countSince(sinceMillis: Long): Int

    @Query(
        """UPDATE device_heartbeats 
           SET syncStatus = :synced, syncedAtMillis = :syncedAt 
           WHERE id = :id"""
    )
    suspend fun markSynced(id: Long, synced: String, syncedAt: Long)

    @Query(
        """DELETE FROM device_heartbeats 
           WHERE syncStatus = :synced AND syncedAtMillis IS NOT NULL AND syncedAtMillis < :before"""
    )
    suspend fun pruneSyncedBefore(synced: String, before: Long)
}
