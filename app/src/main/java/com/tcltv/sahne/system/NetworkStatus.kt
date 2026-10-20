package com.tcltv.sahne.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

sealed interface Connection {
    data object Offline : Connection
    data object Ethernet : Connection

    /** [bars] is 0..3; [ssid] needs location permission; [linkMbps] is the radio link rate, not internet speed. */
    data class Wifi(val bars: Int?, val ssid: String?, val linkMbps: Int?) : Connection

    /** Connected but the network has not passed Android's internet check. */
    data object NoInternet : Connection
}

/** Value Android reports when the SSID is hidden from the app; WifiManager.UNKNOWN_SSID only exists from API 30. */
private const val UNKNOWN_SSID = "<unknown ssid>"

class NetworkStatus(context: Context) {

    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val locationGranted = MutableStateFlow(hasLocationPermission())

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Call after the permission dialog closes, so the SSID appears without restarting. */
    fun onLocationPermissionChanged() {
        locationGranted.value = hasLocationPermission()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val connection: Flow<Connection> = locationGranted.flatMapLatest { granted -> observe(granted) }.distinctUntilChanged()

    private fun observe(locationGranted: Boolean): Flow<Connection> = callbackFlow {
        fun publish(capabilities: NetworkCapabilities?) {
            trySend(classify(capabilities))
        }

        // Android redacts the SSID from network callbacks unless the app holds location permission and asks for it.
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && locationGranted) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = publish(networkCapabilities)
                override fun onLost(network: Network) = publish(null)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = publish(networkCapabilities)
                override fun onLost(network: Network) = publish(null)
            }
        }
        publish(connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities))
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }

    private fun classify(capabilities: NetworkCapabilities?): Connection = when {
        capabilities == null -> Connection.Offline
        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> Connection.NoInternet
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Connection.Ethernet
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            val info = wifiInfo(capabilities)
            Connection.Wifi(
                bars = bars(capabilities),
                ssid = info?.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID },
                linkMbps = info?.linkSpeed?.takeIf { it > 0 },
            )
        }
        else -> Connection.Ethernet
    }

    private fun wifiInfo(capabilities: NetworkCapabilities): WifiInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities.transportInfo as? WifiInfo
        } else {
            // Before Android 12 Wi-Fi details aren't in NetworkCapabilities; this API is deprecated only from 12 on.
            @Suppress("DEPRECATION")
            wifiManager?.connectionInfo
        }

    private fun bars(capabilities: NetworkCapabilities): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rssi = capabilities.signalStrength
        if (rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) return null
        return when {
            rssi >= -55 -> 3
            rssi >= -67 -> 2
            rssi >= -78 -> 1
            else -> 0
        }
    }
}
