package com.example.nyasaplayer.core.common.util

/**
 * The online rule (D72), driven by what the default-network callbacks report about the network they
 * report on — so it cannot read a stale answer — and ignoring events about any network that is no
 * longer the default. A one-off seed fills in until the first callback.
 *
 * Not thread-safe on its own; `NetworkMonitor` calls it under one lock.
 */
internal class DefaultNetworkState {

    private var current: Long? = null
    private var heardFromCallback = false

    var isOnline: Boolean = false
        private set

    /** The starting value, applied only if no callback has spoken yet — a callback is newer. */
    fun seed(network: Long?, hasInternet: Boolean, isCaptivePortal: Boolean) {
        if (heardFromCallback) return
        current = network
        isOnline = network != null && hasInternet && !isCaptivePortal
    }

    /** Decides nothing: the answer waits for this network's capabilities, as the platform asks. */
    fun available(network: Long) {
        heardFromCallback = true
        current = network
    }

    fun capabilities(network: Long, hasInternet: Boolean, isCaptivePortal: Boolean) {
        heardFromCallback = true
        if (network == current) isOnline = hasInternet && !isCaptivePortal
    }

    fun lost(network: Long) {
        heardFromCallback = true
        if (network == current) {
            current = null
            isOnline = false
        }
    }
}
