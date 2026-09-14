package com.example.nyasaplayer.auto.ui

import com.example.nyasaplayer.auto.viewmodel.AutomotiveUiState
import com.example.nyasaplayer.core.playback.PlaybackSnapshot
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When the overlay's Skip next has somewhere to skip to (A8). The overlay's own display rules live
 * in CarErrorOverlayTest; this covers what AutomotiveApp hands it.
 */
class SkipNextGateTest {

    private fun state(hasNext: Boolean, queueSize: Int, isOffline: Boolean) = AutomotiveUiState(
        playback = PlaybackSnapshot(hasNext = hasNext, queueSize = queueSize),
        isOffline = isOffline,
    )

    @Test
    fun `offered when there is another track online`() {
        val gate = skipNextOrNull(state(hasNext = true, queueSize = 2, isOffline = false)) {}

        assertNotNull(gate)
    }

    @Test
    fun `not offered for a one-item queue even under repeat-all`() {
        val gate = skipNextOrNull(state(hasNext = true, queueSize = 1, isOffline = false)) {}

        assertNull(gate)
    }

    @Test
    fun `not offered offline`() {
        val gate = skipNextOrNull(state(hasNext = true, queueSize = 2, isOffline = true)) {}

        assertNull(gate)
    }

    @Test
    fun `not offered without a next track`() {
        val gate = skipNextOrNull(state(hasNext = false, queueSize = 2, isOffline = false)) {}

        assertNull(gate)
    }
}
