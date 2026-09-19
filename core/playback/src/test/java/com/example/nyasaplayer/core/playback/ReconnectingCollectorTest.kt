package com.example.nyasaplayer.core.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * What a collector does when the controller is gone — commands rebuild it (T14), reads refuse (T15).
 *
 * The sequence a user produces without trying: back out of the app, come back, press play. Before
 * this, the controller had been released and nothing could rebuild it — every command failed for the
 * rest of the process's life.
 */
@RunWith(RobolectricTestRunner::class)
class ReconnectingCollectorTest {

    private lateinit var session: MediaSession
    private lateinit var connection: ControllerConnection
    private lateinit var collector: TestCollector

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        session = MediaSession.Builder(context, ReconnectPlayer()).setId("t14-reconnect").build()
        connection = ControllerConnection(context, session.token)
        collector = TestCollector(connection)
        collector.connectController()
        idle()
    }

    @After
    fun tearDown() {
        collector.releaseController()
        session.release()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Drops the connection the way a departing ViewModel used to. */
    private fun loseTheController() {
        connection.release()
        idle()
    }

    /**
     * T27: the tripwire T16 closed without. A disconnected controller is the state nothing known
     * produces in a live process, so the one report is the evidence.
     */
    @Test
    fun aCommandAgainstADisconnectedController_reportsItOnce() {
        loseTheController()

        assertFalse(collector.transport.play())
        idle()

        assertEquals("the disconnected controller should be reported", 1, collector.disconnectedReports)
    }

    /** A reconnect already in flight swallows later taps, and must not report them either. */
    @Test
    fun repeatedCommandsDuringOneRebuild_reportOnce() {
        loseTheController()

        collector.transport.play()
        collector.transport.play()
        collector.transport.play()
        idle()

        assertEquals("one attempt, one report", 1, collector.disconnectedReports)
    }

    @Test
    fun aCommandAgainstALostController_rebuildsTheConnection() {
        loseTheController()
        assertFalse("precondition: the controller really is gone", collector.transport.play())
        idle()

        // The command that found it dead is deliberately not replayed — the next one works.
        assertTrue(collector.transport.play())
        assertEquals("no failure should have been reported", 0, collector.unavailableReports)
    }

    @Test
    fun aSuccessfulReconnect_reportsNothingToTheSurface() {
        loseTheController()
        collector.transport.skipNext()
        idle()

        assertEquals(0, collector.unavailableReports)
        assertTrue("the collector holds a live controller again", collector.controller!!.isConnected)
    }

    @Test
    fun repeatedFailuresWhileReconnecting_doNotQueueAttempts() {
        loseTheController()

        // Three taps before the connection can complete.
        collector.transport.play()
        collector.transport.skipNext()
        collector.transport.pause()
        idle()

        assertTrue(collector.controller!!.isConnected)
        assertEquals("one attempt, not three", 0, collector.unavailableReports)
    }

    // ── Reads against the same lost controller (T15) ──

    @Test
    fun hasNextTrack_onALostController_answersAsIfThereWereNoController() {
        assertTrue(
            "precondition: two queued items, so a live controller has a next",
            collector.hasNextTrack(RepeatMode.Off),
        )
        loseTheController()

        // Repeat-all is the case that answered true whatever the queue said.
        assertFalse(collector.hasNextTrack(RepeatMode.All))
        assertEquals("a read is not a user action: nothing reported", 0, collector.unavailableReports)
    }

    @Test
    fun restoreIfIdle_onALostController_neverRunsTheRead() = runTest {
        loseTheController()
        var askedForTheSession = false

        val restored = collector.restoreIfIdle {
            askedForTheSession = true
            null
        }

        assertNull(restored)
        assertFalse("no session read against a player that cannot receive it", askedForTheSession)
    }

    @Test
    fun reconnectAfterTheSessionIsGone_reportsUnavailable() {
        session.release()
        idle()
        loseTheController()

        collector.transport.play()
        idle()

        assertEquals("nothing to reconnect to, so the surface is told", 1, collector.unavailableReports)
    }
}

private class TestCollector(connection: ControllerConnection) :
    BasePlayerStateCollector(connection, TestScope()) {

    var unavailableReports = 0
    var disconnectedReports = 0

    override val positionPollIntervalMs: Long = 1_000L

    override fun onPlayerUnavailable() {
        unavailableReports++
    }

    override fun onControllerFoundDisconnected() {
        disconnectedReports++
    }
}

/** Shared with [NeverConnectedCollectorTest]: any session needs a player behind it. */
class ReconnectPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    override fun getState(): State =
        State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(
                listOf(
                    MediaItemData.Builder("a").setMediaItem(MediaItem.EMPTY).build(),
                    MediaItemData.Builder("b").setMediaItem(MediaItem.EMPTY).build(),
                ),
            )
            .build()

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> =
        Futures.immediateVoidFuture()
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> =
        Futures.immediateVoidFuture()
}
