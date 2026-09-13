package com.example.nyasaplayer.core.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.example.nyasaplayer.core.common.models.Song
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The snapshot knows whether the player is *trying* to play (A8).
 *
 * `isPlaying` is false while buffering, so it cannot tell a driver's play attempt from a restore that
 * prepared the player and paused it. The car's offline guard needs exactly that difference.
 */
@RunWith(RobolectricTestRunner::class)
class PlayWhenReadySnapshotTest {

    private lateinit var session: MediaSession
    private lateinit var collector: SnapshotCollector

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        session = MediaSession.Builder(context, PlayWhenReadyPlayer()).setId("a8-play-when-ready").build()
        collector = SnapshotCollector(ControllerConnection(context, session.token))
        collector.connectController()
        idle()
    }

    @After
    fun tearDown() {
        collector.releaseController()
        session.release()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun playWhenReady_followsPlayAndPause() {
        assertFalse(
            "precondition: a fresh player is not trying to play",
            collector.playbackState.value.playWhenReady,
        )

        collector.transport.play()
        idle()
        assertTrue(collector.playbackState.value.playWhenReady)

        collector.transport.pause()
        idle()
        assertFalse(collector.playbackState.value.playWhenReady)
    }

    @Test
    fun applyRestored_publishesANotPlayingSession() {
        collector.transport.play()
        idle()

        val song = Song(mediaId = "a", title = "A")
        collector.applyRestored(
            RestoredPlayback(
                queue = listOf(song),
                index = 0,
                song = song,
                positionMs = 0L,
                repeatMode = RepeatMode.Off,
            ),
        )

        assertFalse(collector.playbackState.value.playWhenReady)
    }
}

private class SnapshotCollector(connection: ControllerConnection) :
    BasePlayerStateCollector(connection, TestScope()) {
    override val positionPollIntervalMs: Long = 1_000L
}

/** Reports its own playWhenReady, so the session tells the controller when it changes. */
private class PlayWhenReadyPlayer : SimpleBasePlayer(Looper.getMainLooper()) {

    private var playWhenReady = false

    override fun getState(): State =
        State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(listOf(MediaItemData.Builder("a").setMediaItem(MediaItem.EMPTY).build()))
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
}
