package com.example.nyasaplayer.core.playback

import android.os.Bundle
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import com.example.nyasaplayer.core.common.models.Song
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The connected half of playback, on the JVM.
 *
 * `MediaController` is `@DoNotMock` with a package-private constructor, so for four tickets running
 * every claim about a *live* controller had to be made on a device — T3's restore command, T13's
 * transport, T11's disconnected branch. This stands up a real `MediaSession` over a fake player
 * under Robolectric and connects a real controller to it, which is the only way to assert what the
 * transport actually sent (T17).
 *
 * Two kinds of operation are covered: those that reach the player through Media3's own commands
 * (play, seek, repeat), and those that go out as custom commands to `PlaybackService` (shuffle),
 * which the session callback records here instead.
 */
@RunWith(RobolectricTestRunner::class)
class ConnectedTransportTest {

    private lateinit var player: RecordingPlayer
    private lateinit var callback: RecordingCallback
    private lateinit var session: MediaSession
    private lateinit var controller: MediaController
    private lateinit var transport: PlayerTransport

    private var unavailableReports = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        player = RecordingPlayer()
        callback = RecordingCallback()
        session = MediaSession.Builder(context, player).setId("t17").setCallback(callback).build()
        val future = MediaController.Builder(context, session.token).buildAsync()
        idle()
        controller = future.get()
        idle()
        transport = PlayerTransport(controller = { controller }, onUnavailable = { unavailableReports++ })
    }

    @After
    fun tearDown() {
        controller.release()
        session.release()
        player.release()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun controller_connects() {
        assertTrue(controller.isConnected)
    }

    // ── Player-level commands ──

    @Test
    fun play_thenPause_reachThePlayer() {
        assertTrue(transport.play())
        idle()
        assertTrue(transport.pause())
        idle()

        assertEquals(listOf("play", "pause"), player.calls)
        assertEquals(0, unavailableReports)
    }

    @Test
    fun seekTo_reachesThePlayerWithThePosition() {
        assertTrue(transport.seekTo(42_000L))
        idle()

        assertEquals(listOf("seek@42000"), player.calls)
    }

    @Test
    fun toggleRepeatMode_walksOffToAll() {
        assertTrue(transport.toggleRepeatMode())
        idle()

        assertEquals(listOf("repeat=${Player.REPEAT_MODE_ALL}"), player.calls)
    }

    // ── Custom commands, the ones PlaybackService handles ──

    @Test
    fun toggleShuffle_sendsTheCustomCommand() {
        assertTrue(transport.toggleShuffle())
        idle()

        assertEquals(listOf(PlaybackCommands.CMD_TOGGLE_SHUFFLE), callback.customCommands)
    }

    @Test
    fun setQueue_sendsTheCommandCarryingItsSongs() {
        val songs = listOf(Song(mediaId = "a", title = "A"), Song(mediaId = "b", title = "B"))

        assertTrue(transport.setQueue(songs, startIndex = 1))
        idle()

        assertEquals(listOf(PlaybackCommands.CMD_SET_QUEUE), callback.customCommands)
        assertEquals(1, callback.lastArgs?.getInt(PlaybackCommands.KEY_START_INDEX))
    }

    @Test
    fun skipNext_thenSkipPrevious_reachThePlayer() {
        assertTrue(transport.skipNext())
        idle()
        assertTrue(transport.skipPrevious())
        idle()

        assertEquals(listOf("next", "previous"), player.calls)
    }

    @Test
    fun sendRestoreState_carriesTheWholeSession() {
        // T3 could only assert this on a device: that the restore command leaves with its queue,
        // index, position and repeat mode attached.
        val restored = RestoredPlayback(
            queue = listOf(Song(mediaId = "a", title = "A"), Song(mediaId = "b", title = "B")),
            index = 1,
            song = Song(mediaId = "b", title = "B"),
            positionMs = 42_000L,
            repeatMode = RepeatMode.All,
        )

        controller.sendRestoreState(restored)
        idle()

        assertEquals(listOf(PlaybackCommands.CMD_RESTORE_STATE), callback.customCommands)
        val args = requireNotNull(callback.lastArgs)
        assertEquals(1, args.getInt(PlaybackCommands.KEY_START_INDEX))
        assertEquals(42_000L, args.getLong(PlaybackCommands.KEY_POSITION_MS))
        assertEquals(RepeatMode.All.name, args.getString(PlaybackCommands.KEY_REPEAT_MODE))
    }

    // ── Refusals: connected, declined by a guard, and silent (D62) ──

    @Test
    fun removeFromQueue_onTheCurrentItem_refusesWithoutReporting() {
        // The case T11's plan had to leave untested: `true` means the controller was reached, and a
        // guard declining is not a failure — wiring an error here would put a dialog in front of a
        // driver who tapped remove on the track that is playing.
        assertTrue(transport.queue.removeFromQueue(0))
        idle()

        assertEquals(emptyList<String>(), player.calls)
        assertEquals(0, unavailableReports)
    }

    @Test
    fun skipToQueueItem_outOfRange_refusesWithoutReporting() {
        assertTrue(transport.queue.skipToQueueItem(99))
        idle()

        assertEquals(emptyList<String>(), player.calls)
        assertEquals(0, unavailableReports)
    }

    @Test
    fun skipToQueueItem_inRange_reachesThePlayer() {
        assertTrue(transport.queue.skipToQueueItem(1))
        idle()

        assertTrue(player.calls.contains("skipTo@1"))
        assertEquals(0, unavailableReports)
    }

    // ── The condition T11 could not reach ──

    @Test
    fun releasedController_isNotConnected_andEveryCommandRefuses() {
        controller.release()
        idle()

        assertFalse(controller.isConnected)
        assertFalse(transport.play())
        assertFalse(transport.skipNext())
        assertFalse(transport.queue.clearQueue())
        assertEquals(3, unavailableReports)
        // Nothing reached the player after the release.
        assertEquals(emptyList<String>(), player.calls)
    }

    @Test
    fun releasedController_isPlayingIsUnknownAndSilent() {
        controller.release()
        idle()

        assertEquals(null, transport.isPlaying())
        assertEquals(0, unavailableReports)
    }
}

private class RecordingCallback : MediaSession.Callback {

    val customCommands = mutableListOf<String>()
    var lastArgs: Bundle? = null

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val commands = SessionCommands.Builder()
            .add(SessionCommand(PlaybackCommands.CMD_SET_QUEUE, Bundle.EMPTY))
            .add(SessionCommand(PlaybackCommands.CMD_SHUFFLE_PLAY, Bundle.EMPTY))
            .add(SessionCommand(PlaybackCommands.CMD_RESTORE_STATE, Bundle.EMPTY))
            .add(SessionCommand(PlaybackCommands.CMD_TOGGLE_SHUFFLE, Bundle.EMPTY))
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(commands)
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        customCommands += customCommand.customAction
        lastArgs = args
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
}

/** Records what the session asked the player to do. */
private class RecordingPlayer : SimpleBasePlayer(Looper.getMainLooper()) {

    val calls = mutableListOf<String>()

    /** Tracked so `hasNextMediaItem`/`hasPreviousMediaItem` answer honestly as the queue moves. */
    private var currentIndex = 0

    override fun getState(): State =
        State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(
                listOf(
                    MediaItemData.Builder("a").setMediaItem(MediaItem.EMPTY).build(),
                    MediaItemData.Builder("b").setMediaItem(MediaItem.EMPTY).build(),
                    MediaItemData.Builder("c").setMediaItem(MediaItem.EMPTY).build(),
                ),
            )
            .setCurrentMediaItemIndex(currentIndex)
            .build()

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        calls += "stop"
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        calls += if (playWhenReady) "play" else "pause"
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        calls += when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> "next"
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> "previous"
            Player.COMMAND_SEEK_TO_MEDIA_ITEM -> "skipTo@$mediaItemIndex"
            else -> "seek@$positionMs"
        }
        if (mediaItemIndex != androidx.media3.common.C.INDEX_UNSET) {
            currentIndex = mediaItemIndex
            invalidateState()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        calls += "repeat=$repeatMode"
        return Futures.immediateVoidFuture()
    }
}
