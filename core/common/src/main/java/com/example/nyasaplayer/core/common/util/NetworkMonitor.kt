package com.example.nyasaplayer.core.common.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the default network can carry traffic: `INTERNET` and not a `CAPTIVE_PORTAL` (D72).
 *
 * Every answer comes from the callbacks' own arguments. The platform documents that synchronous
 * `ConnectivityManager` reads inside these callbacks may be stale; doing exactly that missed about one
 * network loss in ten on the AAOS emulator (T29).
 */
@Singleton
class NetworkMonitor @Inject constructor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val lock = Any()
    private val state = DefaultNetworkState()
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        connectivityManager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = update {
                    state.available(network.networkHandle)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        // API 24–25 do not promise onCapabilitiesChanged after onAvailable, so read it —
                        // the one synchronous read left. Null counts as online: onAvailable has just
                        // declared this network the default and ready for use.
                        val caps = connectivityManager.getNetworkCapabilities(network)
                        state.capabilities(
                            network.networkHandle,
                            hasInternet = caps?.hasInternet() ?: true,
                            isCaptivePortal = caps?.isCaptivePortal() ?: false,
                        )
                    }
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) = update {
                    state.capabilities(
                        network.networkHandle,
                        hasInternet = capabilities.hasInternet(),
                        isCaptivePortal = capabilities.isCaptivePortal(),
                    )
                }

                override fun onLost(network: Network) = update {
                    state.lost(network.networkHandle)
                }
            },
        )
        // Seed after registering: a network lost before registration can never reach onLost, so a seed
        // read earlier could stay "online" indefinitely. Any callback that has already arrived wins.
        val active = connectivityManager.activeNetwork
        val caps = active?.let { connectivityManager.getNetworkCapabilities(it) }
        update {
            state.seed(
                active?.networkHandle,
                hasInternet = caps?.hasInternet() == true,
                isCaptivePortal = caps?.isCaptivePortal() == true,
            )
        }
    }

    /** Callbacks run on ConnectivityThread and the seed on the constructing thread: one lock for both. */
    private inline fun update(change: () -> Unit) = synchronized(lock) {
        change()
        _isOnline.value = state.isOnline
    }
}

private fun NetworkCapabilities.hasInternet() = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

private fun NetworkCapabilities.isCaptivePortal() =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
