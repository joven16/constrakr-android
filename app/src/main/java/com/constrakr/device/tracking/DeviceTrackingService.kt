package com.constrakr.device.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.constrakr.BuildConfig
import com.constrakr.domain.DeviceStore
import com.constrakr.domain.JobSiteStore
import com.constrakr.kiosk.KioskController
import com.constrakr.kiosk.KioskMaintenanceSession
import com.constrakr.kiosk.KioskSettings
import com.constrakr.network.DeviceHeartbeatRequest
import com.constrakr.util.NetworkMonitor
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Collects kiosk device telemetry. GPS fixes must be ≤30 m accuracy; no continuous GPS. */
class DeviceTrackingService(
    private val context: Context,
    private val deviceStore: DeviceStore,
    private val jobSiteStore: JobSiteStore,
    private val networkMonitor: NetworkMonitor,
    private val kioskSettings: KioskSettings,
    private val maintenanceSession: KioskMaintenanceSession,
    private val kioskController: KioskController
) {
    data class Snapshot(
        val request: DeviceHeartbeatRequest,
        val hadLocation: Boolean
    )

    @SuppressLint("MissingPermission")
    suspend fun collectSnapshot(): Snapshot {
        val now = System.currentTimeMillis()
        val battery = readBattery()
        val location = readLocationOnce()
        val online = networkMonitor.isOnline.value
        val kioskActive = kioskSettings.isKioskEnabled &&
            kioskController.isDeviceOwner &&
            !maintenanceSession.isActive.value

        val request = DeviceHeartbeatRequest(
            deviceId = deviceStore.localId,
            siteId = jobSiteStore.defaultSiteId?.toString(),
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.accuracy,
            batteryPercent = battery.first,
            isCharging = battery.second,
            networkType = readNetworkType(),
            isOnline = online,
            isKioskModeActive = kioskActive,
            deviceModel = Build.MODEL.orEmpty(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            appVersion = BuildConfig.VERSION_NAME,
            timestamp = now
        )
        return Snapshot(request, location != null)
    }

    private fun readBattery(): Pair<Int, Boolean> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return percent to charging
    }

    @SuppressLint("MissingPermission")
    private suspend fun readLocationOnce(): Location? {
        if (!hasLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val last = runCatching { client.lastLocation.await() }.getOrNull()
        if (last != null && locationAgeMs(last) <= STALE_LOCATION_MS) {
            last.toHeartbeatFix()?.let { return it }
        }

        val token = CancellationTokenSource()
        val current = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
        }?.toHeartbeatFix()
        if (current != null) return current

        return null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun readNetworkType(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "unknown"
        return when {
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
    }

    private fun locationAgeMs(location: Location): Long {
        val fix = location.elapsedRealtimeNanos
        return if (fix > 0L) {
            (android.os.SystemClock.elapsedRealtimeNanos() - fix) / 1_000_000L
        } else {
            Long.MAX_VALUE
        }
    }

    companion object {
        private const val LOCATION_TIMEOUT_MS = 12_000L
        private const val STALE_LOCATION_MS = 5 * 60_000L
    }
}
