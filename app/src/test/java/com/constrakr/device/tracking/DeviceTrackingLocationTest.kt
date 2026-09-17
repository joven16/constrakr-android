package com.constrakr.device.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTrackingLocationTest {
    @Test
    fun isHeartbeatLocationAcceptable_allowsUpTo5Meters() {
        assertTrue(isHeartbeatLocationAcceptable(5f))
        assertTrue(isHeartbeatLocationAcceptable(3.2f))
        assertFalse(isHeartbeatLocationAcceptable(5.1f))
        assertFalse(isHeartbeatLocationAcceptable(30f))
        assertFalse(isHeartbeatLocationAcceptable(null))
        assertFalse(isHeartbeatLocationAcceptable(0f))
        assertFalse(isHeartbeatLocationAcceptable(-1f))
    }
}
