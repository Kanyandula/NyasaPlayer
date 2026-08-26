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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a restored session looks like, asserted without a `MediaController`.
 *
 * The collector never asks its [ControllerConnection] for anything until `connectController()`, which
 * this never calls — so no controller is ever built, and every assertion below is about the snapshot
 * alone. Robolectric is here only because a `ControllerConnection` needs a `Context` and a
 * `SessionToken` to exist (T14); before that it took a future and needed neither.
 */
@RunWith(RobolectricTestRunner::class)
class RestoredSnapshotTest {

    private lateinit var session: MediaSession
    private lateinit var collector: BasePlayerStateCollector

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        session = MediaSession.Builder(context, UnusedPlayer()).setId("restored").build()
        val connection = ControllerConnection(context, session.token)
        collector = object : BasePlayerStateCollector(connection, TestScope()) {
            override val positionPollIntervalMs: Long = 1_000L
        }
    }

    @After
    fun tearDown() = session.release()

    private fun song(id: String, durationMs: Long = 0L) =
        Song(mediaId = id, title = id.uppercase(), durationMs = durationMs)

    private fun restored(
        queue: List<Song>,
        index: Int,
        positionMs: Long = 0L,
        repeatMode: RepeatMode = RepeatMode.Off,
    ) = RestoredPlayback(
        queue = queue,
        index = index,
        song = queue[index],
        positionMs = positionMs,
        repeatMode = repeatMode,
    )

    @Test
    fun applyRestored_publishesEveryFieldFromTheRestoredValue() {
        val queue = listOf(song("a"), song("b", durationMs = 222_000L), song("c"))

        collector.applyRestored(restored(queue, index = 1, positionMs = 42_000L, RepeatMode.One))

        val snapshot = collector.playbackState.value
        assertEquals("b", snapshot.currentSong?.mediaId)
        assertEquals(42_000L, snapshot.currentPositionMs)
        assertEquals(222_000L, snapshot.durationMs)
        assertEquals(RepeatMode.One, snapshot.repeatMode)
        assertEquals(queue, snapshot.queue)
        assertEquals(3, snapshot.queueSize)
        assertEquals(1, snapshot.currentQueueIndex)
    }

    @Test
    fun applyRestored_neverPublishesAPlayingSession() {
        collector.updateSnapshot { it.copy(isPlaying = true) }

        collector.applyRestored(restored(listOf(song("a")), index = 0))

        assertFalse(collector.playbackState.value.isPlaying)
    }

    // ── hasNext / hasPrevious come from the restored value, never from the controller ──

    @Test
    fun applyRestored_lastTrackWithRepeatOff_hasNoNext() {
        val queue = listOf(song("a"), song("b"))

        collector.applyRestored(restored(queue, index = 1, repeatMode = RepeatMode.Off))

        assertFalse(collector.playbackState.value.hasNext)
    }

    @Test
    fun applyRestored_lastTrackWithRepeatAll_hasNext() {
        val queue = listOf(song("a"), song("b"))

        collector.applyRestored(restored(queue, index = 1, repeatMode = RepeatMode.All))

        assertTrue(collector.playbackState.value.hasNext)
    }

    @Test
    fun applyRestored_firstTrack_hasNoPrevious() {
        val queue = listOf(song("a"), song("b"))

        collector.applyRestored(restored(queue, index = 0))

        assertFalse(collector.playbackState.value.hasPrevious)
    }

    @Test
    fun applyRestored_secondTrack_hasPrevious() {
        val queue = listOf(song("a"), song("b"))

        collector.applyRestored(restored(queue, index = 1))

        assertTrue(collector.playbackState.value.hasPrevious)
    }

    @Test
    fun applyRestored_singleItemQueueWithRepeatOff_hasNeitherDirection() {
        collector.applyRestored(restored(listOf(song("a")), index = 0))

        val snapshot = collector.playbackState.value
        assertFalse(snapshot.hasPrevious)
        assertFalse(snapshot.hasNext)
    }
}

/** Never used: `connectController()` is never called, so nothing connects to this session. */
private class UnusedPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    override fun getState(): State =
        State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(listOf(MediaItemData.Builder("a").setMediaItem(MediaItem.EMPTY).build()))
            .build()

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
}
