package com.constrakr.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the device has an active internet-capable link (Wi‑Fi / cellular / etc.).
 * Uses the same signal as the status-bar icons — not Android's slow "validated" probe.
 */
class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(isOnlineNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        refresh()
    }

    private fun refresh() {
        _isOnline.value = isOnlineNow()
    }

    private fun isOnlineNow(): Boolean {
        val active = connectivityManager.activeNetwork
        if (active != null && hasInternetLink(active)) return true
        // Some devices (e.g. Samsung dual-SIM / Wi‑Fi+cellular) lag updating activeNetwork.
        return connectivityManager.allNetworks.any(::hasInternetLink)
    }

    private fun hasInternetLink(network: Network): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        // Suspended = airplane-style pause; absent NOT_SUSPENDED means link is down.
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)) return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}
