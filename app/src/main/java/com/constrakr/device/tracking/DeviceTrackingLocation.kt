package com.constrakr.device.tracking

import android.location.Location

/** Heartbeats only include GPS fixes at or below this accuracy (meters). */
const val MAX_HEARTBEAT_GPS_ACCURACY_METERS = 30f

fun isHeartbeatLocationAcceptable(accuracyMeters: Float?): Boolean {
    if (accuracyMeters == null || accuracyMeters <= 0f) return false
    return accuracyMeters <= MAX_HEARTBEAT_GPS_ACCURACY_METERS
}

fun Location?.toHeartbeatFix(): Location? {
    val location = this ?: return null
    if (!location.hasAccuracy()) return null
    return if (isHeartbeatLocationAcceptable(location.accuracy)) location else null
}
