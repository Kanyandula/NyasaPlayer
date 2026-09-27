package com.example.nyasaplayer.core.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.example.nyasaplayer.core.common.models.Song
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * T00: the media template, Assistant and Bluetooth play by media id through `onAddMediaItems`,
 * bypassing the custom commands that fill [PlaybackQueueManager]. The service's timeline listener
 * syncs the manager from the player, so `saveState` persists the queue actually playing rather
 * than the last one the custom launcher set.
 */
@RunWith(RobolectricTestRunner::class)
class TemplateQueueSyncTest {

    private val songs = listOf("a", "b", "c").associateWith { Song(mediaId = it, title = it.uppercase()) }

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var controller: MediaController
    private val queueManager = PlaybackQueueManager()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        player = ExoPlayer.Builder(context).build()
        // Same wiring as PlaybackService.playerListener.
        player.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) queueManager.syncWith(player)
                }
            },
        )
        session = MediaSession.Builder(context, player).setCallback(ResolvingCallback()).build()
        val future = MediaController.Builder(context, session.token).buildAsync()
        idle()
        controller = future.get()
    }

    @After
    fun tearDown() {
        controller.release()
        session.release()
        player.release()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Plays [ids] the way the template's `playFromMediaId` does: bare ids, resolved by the service. */
    private fun playFromTemplate(ids: List<String>, startIndex: Int) {
        controller.setMediaItems(ids.map { MediaItem.Builder().setMediaId(it).build() }, startIndex, 0L)
        idle()
    }

    @Test
    fun template_playback_populates_queue_manager() {
        playFromTemplate(listOf("b", "c"), startIndex = 1)

        assertEquals(listOf("b", "c"), queueManager.queueSongIds())
        assertEquals(listOf("B", "C"), queueManager.queue.map { it.title })
        assertEquals(1, queueManager.currentIndex)
    }

    @Test
    fun template_playback_replaces_a_stale_launcher_queue() {
        queueManager.setQueueShuffled(songs.values.toList())
        applyQueueManagerToPlayer()

        playFromTemplate(listOf("c"), startIndex = 0)

        assertEquals(listOf("c"), queueManager.queueSongIds())
        assertEquals(0, queueManager.currentIndex)
        assertEquals(false, queueManager.isShuffled)
    }

    @Test
    fun launcher_queue_keeps_its_shuffle_state_when_applied_to_the_player() {
        queueManager.setQueueShuffled(songs.values.toList())
        val shuffledOrder = queueManager.queueSongIds()

        applyQueueManagerToPlayer()

        assertTrue(queueManager.isShuffled)
        assertEquals(shuffledOrder, queueManager.queueSongIds())
    }

    @Test
    fun clearing_the_player_empties_the_queue() {
        playFromTemplate(listOf("a", "b"), startIndex = 0)

        controller.clearMediaItems()
        idle()

        assertEquals(emptyList<String>(), queueManager.queueSongIds())
        assertEquals(-1, queueManager.currentIndex)
    }

    /** What `PlaybackService.applyQueueToPlayer` does after a custom command sets the queue. */
    private fun applyQueueManagerToPlayer() {
        player.setMediaItems(queueManager.queue.map { it.toMediaItem() }, queueManager.currentIndex, 0L)
        idle()
    }

    private inner class ResolvingCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.mapNotNull { songs[it.mediaId]?.toMediaItem() }.toMutableList())
    }
}
