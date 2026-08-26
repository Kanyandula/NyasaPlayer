package com.example.nyasaplayer.core.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The ownership rules that replace "one shared future everybody releases" (T14).
 *
 * Connects against a real `MediaSession` rather than `PlaybackService`, which is what makes these
 * assertions possible on the JVM at all (D64).
 */
@RunWith(RobolectricTestRunner::class)
class ControllerConnectionTest {

    private lateinit var session: MediaSession
    private lateinit var connection: ControllerConnection

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        session = MediaSession.Builder(context, InertConnectionPlayer()).setId("t14-conn").build()
        connection = ControllerConnection(context, session.token)
    }

    @After
    fun tearDown() = session.release()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun twoConsumers_shareOneConnection() {
        val first = connection.acquire()
        val second = connection.acquire()

        assertSame("one process, one controller", first, second)
    }

    @Test
    fun oneConsumerLeaving_leavesTheConnectionAliveForTheOther() {
        // The T14 bug, in one test: this used to kill playback for everything still running.
        val shared = connection.acquire()
        connection.acquire()
        idle()

        connection.release()
        idle()

        assertTrue("the remaining consumer still has a live controller", shared.get().isConnected)
    }

    @Test
    fun theLastConsumerLeaving_releasesTheController() {
        val shared = connection.acquire()
        idle()

        connection.release()
        idle()

        assertFalse("nothing is using playback, so it should not stay bound", shared.get().isConnected)
    }

    @Test
    fun reconnect_replacesADeadConnectionWithALiveOne() {
        val dead = connection.acquire()
        idle()
        connection.release()
        idle()
        assertFalse(dead.get().isConnected)

        val fresh = connection.acquire()
        val reconnected = connection.reconnect()
        idle()

        assertNotSame("a released future cannot be revived, only replaced", dead, reconnected)
        assertTrue(reconnected.get().isConnected)
        assertNotSame(fresh, reconnected)
    }

    @Test
    fun releasingMoreOftenThanAcquiring_doesNotWedgeTheCount() {
        connection.release()
        connection.release()

        val after = connection.acquire()
        idle()

        assertTrue("an unbalanced release must not stop the next acquire", after.get().isConnected)
    }
}

private class InertConnectionPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
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
