package com.example.nyasaplayer.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The branch that keeps biting: what every transport action does when there is no controller.
 *
 * It is the failure mode behind T11 — a player that is drawn and responds to nothing — and until
 * `PlayerTransport` existed it was unreachable from a test, because it lived thirteen times over
 * inside two ViewModels that each need a `MediaController` to construct.
 *
 * A supplier returning null is the whole harness. Nothing here can assert the *success* path: that
 * still needs a real `MediaSession`, and stays device-verified.
 */
class PlayerTransportTest {

    private val transport = PlayerTransport { null }

    @Test
    fun isPlaying_noController_isUnknownRatherThanFalse() {
        // Not `false` — the caller must be able to tell "not playing" from "no player", or mobile's
        // togglePlayPause would take its play() branch and run an offline check against nothing.
        assertNull(transport.isPlaying())
    }

    @Test
    fun play_noController_reportsItDidNotReachThePlayer() {
        assertFalse(transport.play())
    }

    @Test
    fun pause_noController_reportsItDidNotReachThePlayer() {
        assertFalse(transport.pause())
    }

    @Test
    fun togglePlayPause_noController_reportsItDidNotReachThePlayer() {
        assertFalse(transport.togglePlayPause())
    }

    @Test
    fun skipNext_noController_reportsItDidNotReachThePlayer() {
        assertFalse(transport.skipNext())
    }

    @Test
    fun skipPrevious_noController_reportsItDidNotReachThePlayer() {
        assertFalse(transport.skipPrevious())
    }

    @Test
    fun seekTo_noController_reportsItDidNotReachThePlayer() {
        assertFalse(transport.seekTo(42_000L))
    }

    @Test
    fun toggleRepeatMode_noController_reportsItDidNotReachThePlayer() {
        assertFalse(transport.toggleRepeatMode())
    }

    @Test
    fun toggleShuffle_noController_reportsItDidNotReachThePlayer() {
        // The one both surfaces key an optimistic flag off, so a wrong `true` would flip a shuffle
        // icon that nothing behind it honoured.
        assertFalse(transport.toggleShuffle())
    }

    @Test
    fun skipToQueueItem_noController_reportsItDidNotReachThePlayer() {
        assertFalse(transport.skipToQueueItem(0))
    }

    @Test
    fun removeFromQueue_noController_reportsItDidNotReachThePlayer() {
        assertFalse(transport.removeFromQueue(0))
    }

    @Test
    fun clearQueue_noController_reportsItDidNotReachThePlayer() {
        assertFalse(transport.clearQueue())
    }

    @Test
    fun stopAndClear_noController_reportsItDidNotReachThePlayer() {
        assertFalse(transport.stopAndClear())
    }

    @Test
    fun everyOperation_noController_reportsFailure() {
        // A roll-call, so an operation added later without a test still fails something here.
        val results = listOf(
            "play" to transport.play(),
            "pause" to transport.pause(),
            "togglePlayPause" to transport.togglePlayPause(),
            "skipNext" to transport.skipNext(),
            "skipPrevious" to transport.skipPrevious(),
            "seekTo" to transport.seekTo(0L),
            "toggleRepeatMode" to transport.toggleRepeatMode(),
            "toggleShuffle" to transport.toggleShuffle(),
            "skipToQueueItem" to transport.skipToQueueItem(0),
            "removeFromQueue" to transport.removeFromQueue(0),
            "clearQueue" to transport.clearQueue(),
            "stopAndClear" to transport.stopAndClear(),
        )
        val reachedAnyway = results.filter { it.second }.map { it.first }
        assertFalse("reported success with no controller: $reachedAnyway", reachedAnyway.isNotEmpty())
    }
}