package com.constrakr.device.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTrackingLocationTest {
    @Test
    fun isHeartbeatLocationAcceptable_allowsUpTo30Meters() {
        assertTrue(isHeartbeatLocationAcceptable(30f))
        assertTrue(isHeartbeatLocationAcceptable(12.5f))
        assertFalse(isHeartbeatLocationAcceptable(30.1f))
        assertFalse(isHeartbeatLocationAcceptable(100f))
        assertFalse(isHeartbeatLocationAcceptable(null))
        assertFalse(isHeartbeatLocationAcceptable(0f))
        assertFalse(isHeartbeatLocationAcceptable(-1f))
    }
}
