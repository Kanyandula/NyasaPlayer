package com.example.nyasaplayer.core.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The online rule, driven the way `NetworkMonitor`'s callbacks drive it (T29). Plain JVM: networks are
 * `Network.getNetworkHandle()` longs.
 */
class DefaultNetworkStateTest {

    private val a = 101L
    private val b = 202L
    private val state = DefaultNetworkState()

    @Test
    fun nothingYet_isOffline() {
        assertFalse(state.isOnline)
    }

    @Test
    fun availableThenInternet_isOnline() {
        state.available(a)
        state.capabilities(a, hasInternet = true, isCaptivePortal = false)
        assertTrue(state.isOnline)
    }

    @Test
    fun available_aloneDecidesNothing() {
        state.available(a)
        assertFalse(state.isOnline)
    }

    @Test
    fun captivePortal_isOffline() {
        state.available(a)
        state.capabilities(a, hasInternet = true, isCaptivePortal = true)
        assertFalse(state.isOnline)
    }

    @Test
    fun noInternet_isOffline() {
        state.available(a)
        state.capabilities(a, hasInternet = false, isCaptivePortal = false)
        assertFalse(state.isOnline)
    }

    @Test
    fun onlineThenLost_isOffline() {
        state.available(a)
        state.capabilities(a, hasInternet = true, isCaptivePortal = false)
        state.lost(a)
        assertFalse(state.isOnline)
    }

    @Test
    fun lateLostForThePreviousDefault_isIgnored() {
        state.available(a)
        state.capabilities(a, hasInternet = true, isCaptivePortal = false)
        state.available(b)
        state.capabilities(b, hasInternet = true, isCaptivePortal = false)
        state.lost(a)
        assertTrue(state.isOnline)
    }

    @Test
    fun capabilitiesForANetworkThatIsNotCurrent_areIgnored() {
        state.available(a)
        state.capabilities(a, hasInternet = true, isCaptivePortal = false)
        state.capabilities(b, hasInternet = false, isCaptivePortal = false)
        assertTrue(state.isOnline)
    }

    @Test
    fun seedOnline_thenACallbackSaysOffline_isOffline() {
        state.seed(a, hasInternet = true, isCaptivePortal = false)
        state.lost(a)
        assertFalse(state.isOnline)
    }

    @Test
    fun callbackFirst_thenSeed_seedIsIgnored() {
        state.available(a)
        state.capabilities(a, hasInternet = false, isCaptivePortal = false)
        state.seed(a, hasInternet = true, isCaptivePortal = false)
        assertFalse(state.isOnline)
    }

    @Test
    fun availableFirst_thenSeed_seedIsIgnored() {
        // available() alone must count as a callback, or a seed read just after registering could
        // overwrite the network the callback has already reported.
        state.available(a)
        state.seed(a, hasInternet = true, isCaptivePortal = false)
        assertFalse(state.isOnline)
    }

    @Test
    fun lostFirst_thenSeed_seedIsIgnored() {
        state.lost(a)
        state.seed(a, hasInternet = true, isCaptivePortal = false)
        assertFalse(state.isOnline)
    }

    @Test
    fun capabilitiesAfterLoss_areIgnored() {
        state.available(a)
        state.capabilities(a, hasInternet = true, isCaptivePortal = false)
        state.lost(a)
        state.capabilities(a, hasInternet = true, isCaptivePortal = false)
        assertFalse(state.isOnline)
    }

    @Test
    fun seedWithNoNetwork_isOffline() {
        state.seed(null, hasInternet = true, isCaptivePortal = false)
        assertFalse(state.isOnline)
    }
}
