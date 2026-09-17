package com.constrakr.device.tracking

import com.constrakr.network.DeviceHeartbeatRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTrackingRepositoryTest {
    @Test
    fun shouldSkipHeartbeatDuplicate_respectsMinGap() {
        val last = 1_000_000L
        assertTrue(shouldSkipHeartbeatDuplicate(last, nowMillis = last + 5_000, minGapMillis = 15 * 60_000))
        assertFalse(shouldSkipHeartbeatDuplicate(last, nowMillis = last + 20 * 60_000, minGapMillis = 15 * 60_000))
    }

    @Test
    fun shouldSkipHeartbeatDuplicate_whenNoPrior_returnsFalse() {
        assertFalse(shouldSkipHeartbeatDuplicate(null, nowMillis = 1_000L, minGapMillis = 15 * 60_000))
    }

    @Test
    fun heartbeatRequest_roundTripsThroughEntity() {
        val request = DeviceHeartbeatRequest(
            deviceId = "device-1",
            siteId = "site-1",
            latitude = 14.5,
            longitude = 121.0,
            accuracyMeters = 10f,
            batteryPercent = 55,
            isCharging = false,
            networkType = "wifi",
            isOnline = true,
            isKioskModeActive = true,
            deviceModel = "SM-A175F",
            androidVersion = "14",
            appVersion = "1.0.0-alpha72",
            timestamp = 1_700_000_000_000
        )
        val entity = request.toEntity()
        assertEquals(request.deviceId, entity.deviceId)
        assertEquals(request.siteId, entity.siteId)
        assertEquals(14.5, entity.latitude!!, 0.0)
        assertEquals(121.0, entity.longitude!!, 0.0)
        assertEquals(10f, entity.accuracyMeters!!, 0.01f)
        assertEquals(request.batteryPercent, entity.batteryPercent)
        assertEquals(DeviceHeartbeatEntity.SYNC_PENDING, entity.syncStatus)
        assertNull(entity.syncedAtMillis)

        val roundTrip = entity.toRequest()
        assertEquals(request, roundTrip)
    }
}
