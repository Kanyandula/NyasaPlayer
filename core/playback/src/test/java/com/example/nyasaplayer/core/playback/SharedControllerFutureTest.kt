package com.example.nyasaplayer.core.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * What happens to a **shared** controller future when one consumer releases it (T14, step 1).
 *
 * `PlaybackModule` provides a single `@Singleton ListenableFuture<MediaController>`, and both
 * ViewModels call `BasePlayerStateCollector.releaseController()` — which is
 * `MediaController.releaseFuture(...)` on that shared future — from `onCleared()`. This models
 * exactly that sequence: one consumer finishes, and a second one asks the same future for a
 * controller.
 */
@RunWith(RobolectricTestRunner::class)
class SharedControllerFutureTest {

    private lateinit var session: MediaSession
    private lateinit var future: ListenableFuture<MediaController>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        session = MediaSession.Builder(context, InertPlayer()).setId("t14").build()
        future = MediaController.Builder(context, session.token).buildAsync()
        idle()
    }

    @After
    fun tearDown() {
        session.release()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun releasingTheSharedFuture_leavesTheNextConsumerHoldingADeadController() {
        val first = future.get()
        idle()
        assertTrue("a fresh controller should be connected", first.isConnected)

        // What onCleared() does today, on a future the whole process shares.
        MediaController.releaseFuture(future)
        idle()

        // What the next ViewModel gets when it injects the same singleton and connects.
        val second = future.get()
        idle()

        assertSame("the future hands back the same instance", first, second)
        assertFalse(
            "a released controller is not connected — this is the state T11 reports and cannot " +
                "recover from, reached without anything crashing",
            second.isConnected,
        )
    }

    @Test
    fun aDeadControllerRefusesEveryCommandAndReports() {
        var reports = 0
        val controller = future.get()
        idle()
        MediaController.releaseFuture(future)
        idle()

        val transport = PlayerTransport(controller = { controller }, onUnavailable = { reports++ })

        assertFalse(transport.play())
        assertFalse(transport.skipNext())
        assertFalse(transport.queue.clearQueue())
        assertEquals(3, reports)
    }
}

private class InertPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    override fun getState(): State =
        State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(listOf(MediaItemData.Builder("a").setMediaItem(MediaItem.EMPTY).build()))
            .build()

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> =
        Futures.immediateVoidFuture()
}
