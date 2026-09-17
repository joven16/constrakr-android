package com.constrakr.device.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTrackingConfigTest {
    @Test
    fun intervalFor_usesActiveIntervalWhenChargingOrKiosk() {
        val normalMinutes = 30
        val activeMinutes = 15
        val normalGap = normalMinutes.coerceAtLeast(DeviceTrackingConfig.MIN_INTERVAL_MINUTES) * 60_000L
        val activeGap = activeMinutes.coerceAtLeast(DeviceTrackingConfig.MIN_INTERVAL_MINUTES) * 60_000L

        assertEquals(normalGap, intervalFor(normalMinutes, activeMinutes, charging = false, kioskActive = false))
        assertEquals(activeGap, intervalFor(normalMinutes, activeMinutes, charging = true, kioskActive = false))
        assertEquals(activeGap, intervalFor(normalMinutes, activeMinutes, charging = false, kioskActive = true))
    }

    @Test
    fun intervalFor_enforcesMinimumFifteenMinutes() {
        val gap = intervalFor(5, 5, charging = false, kioskActive = false)
        assertEquals(15 * 60_000L, gap)
    }

    private fun intervalFor(normalMinutes: Int, activeMinutes: Int, charging: Boolean, kioskActive: Boolean): Long {
        val minutes = if (charging || kioskActive) activeMinutes else normalMinutes
        return minutes.coerceAtLeast(DeviceTrackingConfig.MIN_INTERVAL_MINUTES) * 60_000L
    }
}
