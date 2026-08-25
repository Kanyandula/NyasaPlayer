package com.example.nyasaplayer.core.playback

import androidx.media3.session.MediaController
import com.example.nyasaplayer.core.common.models.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What a restored session looks like, asserted without a `MediaController`.
 *
 * The collector never touches its controller future until `connectController()`, so a future that
 * never completes is enough to construct one — which is what makes [BasePlayerStateCollector
 * .applyRestored] the first piece of restore code in this project a unit test can reach.
 */
class RestoredSnapshotTest {

    private lateinit var collector: BasePlayerStateCollector

    @Before
    fun setUp() {
        val neverConnects: ListenableFuture<MediaController> = SettableFuture.create()
        collector = object : BasePlayerStateCollector(neverConnects, TestScope()) {
            override val positionPollIntervalMs: Long = 1_000L
        }
    }

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
