package com.constrakr.attendance

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.constrakr.domain.Employee
import com.constrakr.domain.JobSite
import com.constrakr.domain.JobSiteStore
import com.constrakr.domain.SiteGeofenceSettings
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

class SiteLocationGate(
    private val context: Context,
    private val jobSiteStore: JobSiteStore,
    private val geofenceSettings: SiteGeofenceSettings
) {
    sealed class GateError(message: String) : Exception(message) {
        class PermissionDenied : GateError(
            "Location access is required for on-site attendance. " +
                "Enable GPS in Settings → Apps → ConsTrakr → Location."
        )
        class LocationUnavailable : GateError(
            "Could not read GPS. Move outdoors or wait for a signal, then tap Recheck Location."
        )
        class TimedOut : GateError(
            "GPS timed out. Try again outdoors, or turn off site geofence in Job Sites."
        )
        class OutsideSite(siteName: String, distance: Int, radius: Int) :
            GateError("Outside $siteName (${distance}m away; allowed ${radius}m).")
        class WrongJobSite(assigned: String, detected: String?) :
            GateError(
                if (detected != null) {
                    "Wrong job site — assigned to $assigned but you are at $detected."
                } else {
                    "Wrong job site — you must be at $assigned to punch."
                }
            )
        class AssignedSiteUnavailable(name: String) :
            GateError("$name has no map pin on this device. Set coordinates in Job Sites.")
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Resolves a GPS fix — last known, then high-accuracy current, then stale last known. */
    @SuppressLint("MissingPermission")
    suspend fun resolveLocation(): Result<Location> {
        if (!hasLocationPermission()) {
            return Result.failure(GateError.PermissionDenied())
        }
        val client = LocationServices.getFusedLocationProviderClient(context)

        val last = runCatching { client.lastLocation.await() }.getOrNull()
        if (last != null && locationAgeMs(last) <= FRESH_LOCATION_MS) {
            return Result.success(last)
        }

        val token = CancellationTokenSource()
        val current = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
        }
        if (current != null) {
            return Result.success(current)
        }

        val balanced = withTimeoutOrNull(4_000) {
            val t = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, t.token).await()
        }
        if (balanced != null) {
            return Result.success(balanced)
        }

        if (last != null) {
            return Result.success(last)
        }

        return Result.failure(
            if (current == null && balanced == null) GateError.TimedOut() else GateError.LocationUnavailable()
        )
    }

    suspend fun isInsideDefaultSite(): Result<Boolean> {
        if (!geofenceSettings.isRequired) return Result.success(true)
        val site = jobSiteStore.defaultSite ?: return Result.success(false)
        return isInside(site)
    }

    /** Like iOS — returns inside/outside, or failure for permission/GPS errors. */
    suspend fun isInside(site: JobSite): Result<Boolean> {
        if (!site.hasCoordinate) return Result.success(true)
        val loc = resolveLocation().getOrElse { return Result.failure(it) }
        val dist = jobSiteStore.distanceMeters(loc.latitude, loc.longitude, site)
        return Result.success(dist <= site.radiusMeters)
    }

    suspend fun verifyInsideDefaultSiteIfRequired(): Result<Unit> {
        if (!geofenceSettings.isRequired) return Result.success(Unit)
        val site = jobSiteStore.defaultSite
            ?: return Result.failure(GateError.AssignedSiteUnavailable("default"))
        return verifyInside(site)
    }

    suspend fun verifyAttendanceSite(employee: Employee): Result<Unit> {
        val loc = resolveLocation().getOrElse { return Result.failure(it) }
        val assignedId = employee.assignedSiteId
        if (assignedId != null) {
            val assigned = jobSiteStore.site(assignedId)
                ?: return Result.failure(GateError.AssignedSiteUnavailable(employee.assignedSiteName.ifEmpty { "assigned site" }))
            if (!assigned.hasCoordinate) {
                return Result.failure(GateError.AssignedSiteUnavailable(assigned.displayTitle))
            }
            val detected = jobSiteStore.siteContaining(loc.latitude, loc.longitude)
            if (detected != null && detected.id != assigned.id) {
                return Result.failure(GateError.WrongJobSite(assigned.displayTitle, detected.displayTitle))
            }
            return verifyInside(assigned, loc)
        }
        if (geofenceSettings.isRequired) {
            val site = jobSiteStore.defaultSite
                ?: return Result.failure(GateError.AssignedSiteUnavailable("default"))
            return verifyInside(site, loc)
        }
        return Result.success(Unit)
    }

    suspend fun verifyInside(site: JobSite, location: Location? = null): Result<Unit> {
        val loc = if (location != null) {
            Result.success(location)
        } else {
            resolveLocation()
        }.getOrElse { return Result.failure(it) }

        if (!site.hasCoordinate) return Result.success(Unit)
        val dist = jobSiteStore.distanceMeters(loc.latitude, loc.longitude, site).roundToInt()
        if (dist > site.radiusMeters.roundToInt()) {
            return Result.failure(GateError.OutsideSite(site.displayTitle, dist, site.radiusMeters.roundToInt()))
        }
        return Result.success(Unit)
    }

    private fun locationAgeMs(location: Location): Long {
        val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return age / 1_000_000L
    }

    companion object {
        private const val LOCATION_TIMEOUT_MS = 8_000L
        private const val FRESH_LOCATION_MS = 120_000L
    }
}
