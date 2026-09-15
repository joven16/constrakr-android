package com.constrakr.domain

import java.util.UUID

data class JobSite(
    val id: UUID,
    val name: String,
    val locationLabel: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val updatedAtMillis: Long
) {
    val hasCoordinate: Boolean get() = latitude != 0.0 || longitude != 0.0
    val displayTitle: String get() = name.trim().ifEmpty { "Unnamed site" }

    companion object {
        const val MIN_RADIUS = 30.0
        const val MAX_RADIUS = 100.0
        const val DEFAULT_RADIUS = 100.0

        fun clampRadius(value: Double): Double =
            value.coerceIn(MIN_RADIUS, MAX_RADIUS)
    }
}
