package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What every transport action does when there is no usable controller.
 *
 * It is the failure mode behind T11 — a player that is drawn and responds to nothing — and until
 * `PlayerTransport` existed it was unreachable from a test, because it lived thirteen times over
 * inside two ViewModels that each need a `MediaController` to construct.
 *
 * A supplier returning null is the whole harness. **Note the argument is named:** `onUnavailable` is
 * the last parameter, so a bare trailing lambda would bind to the callback, not the controller.
 *
 * What cannot be covered here: the `isConnected == false` branch, and anything a connected
 * controller does — including a guard refusing an operation without reporting. Both need a real
 * `MediaSession`, which this module has no harness for. They are covered by the manual pass in
 * `docs/superpowers/plans/2026-08-26-aaos-t11-dead-controller.md`.
 */
class PlayerTransportTest {

    private var reports = 0
    private val transport = PlayerTransport(controller = { null }, onUnavailable = { reports++ })

    private fun song(id: String) = Song(mediaId = id, title = id.uppercase())

    // ── Commands report ──

    @Test
    fun play_noController_reportsUnavailable() {
        assertFalse(transport.play())
        assertEquals(1, reports)
    }

    @Test
    fun pause_noController_reportsUnavailable() {
        assertFalse(transport.pause())
        assertEquals(1, reports)
    }

    @Test
    fun togglePlayPause_noController_reportsUnavailable() {
        assertFalse(transport.togglePlayPause())
        assertEquals(1, reports)
    }

    @Test
    fun skipNext_noController_reportsUnavailable() {
        assertFalse(transport.skipNext())
        assertEquals(1, reports)
    }

    @Test
    fun skipPrevious_noController_reportsUnavailable() {
        assertFalse(transport.skipPrevious())
        assertEquals(1, reports)
    }

    @Test
    fun seekTo_noController_reportsUnavailable() {
        assertFalse(transport.seekTo(42_000L))
        assertEquals(1, reports)
    }

    @Test
    fun toggleRepeatMode_noController_reportsUnavailable() {
        assertFalse(transport.toggleRepeatMode())
        assertEquals(1, reports)
    }

    @Test
    fun toggleShuffle_noController_reportsUnavailable() {
        // Both surfaces flip an optimistic shuffle flag off this return value.
        assertFalse(transport.toggleShuffle())
        assertEquals(1, reports)
    }

    @Test
    fun stopAndClear_noController_reportsUnavailable() {
        assertFalse(transport.stopAndClear())
        assertEquals(1, reports)
    }

    // ── The play-entry paths, which used to bypass the transport entirely ──

    @Test
    fun setQueue_noController_reportsAndDoesNotDispatch() {
        // The one that produced the false "playing" state: the ViewModel may only paint a track as
        // playing when this returns true.
        assertFalse(transport.setQueue(listOf(song("a")), startIndex = 0))
        assertEquals(1, reports)
    }

    @Test
    fun shufflePlay_noController_reportsAndDoesNotDispatch() {
        assertFalse(transport.shufflePlay(listOf(song("a"), song("b"))))
        assertEquals(1, reports)
    }

    // ── Queue mutations ──

    @Test
    fun skipToQueueItem_noController_reportsUnavailable() {
        assertFalse(transport.queue.skipToQueueItem(0))
        assertEquals(1, reports)
    }

    @Test
    fun removeFromQueue_noController_reportsUnavailable() {
        assertFalse(transport.queue.removeFromQueue(0))
        assertEquals(1, reports)
    }

    @Test
    fun clearQueue_noController_reportsUnavailable() {
        assertFalse(transport.queue.clearQueue())
        assertEquals(1, reports)
    }

    // ── Queries stay silent ──

    @Test
    fun isPlaying_noController_isUnknownAndReportsNothing() {
        // Not `false` — the caller must tell "not playing" from "no player", or mobile's
        // togglePlayPause takes its play() branch against nothing. And not a report: an error
        // belongs to a user action, not to reading state, or one tap raises two errors.
        assertNull(transport.isPlaying())
        assertEquals(0, reports)
    }

    // ── Roll-call ──

    @Test
    fun everyCommand_noController_reportsExactlyOnceEach() {
        val results = listOf(
            "play" to transport.play(),
            "pause" to transport.pause(),
            "togglePlayPause" to transport.togglePlayPause(),
            "skipNext" to transport.skipNext(),
            "skipPrevious" to transport.skipPrevious(),
            "seekTo" to transport.seekTo(0L),
            "toggleRepeatMode" to transport.toggleRepeatMode(),
            "toggleShuffle" to transport.toggleShuffle(),
            "stopAndClear" to transport.stopAndClear(),
            "setQueue" to transport.setQueue(listOf(song("a")), 0),
            "shufflePlay" to transport.shufflePlay(listOf(song("a"))),
            "skipToQueueItem" to transport.queue.skipToQueueItem(0),
            "removeFromQueue" to transport.queue.removeFromQueue(0),
            "clearQueue" to transport.queue.clearQueue(),
        )
        val dispatchedAnyway = results.filter { it.second }.map { it.first }
        assertEquals("reported dispatch with no controller: $dispatchedAnyway", 0, dispatchedAnyway.size)
        // One report per command, not per internal call — an operation added later without its own
        // test still lands here.
        assertEquals(results.size, reports)
    }
}
