package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.playback.PlaybackSnapshot
import com.example.nyasaplayer.core.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** T04: the full player shows the next track, derived from the queue, and opens the queue on tap. */
@RunWith(RobolectricTestRunner::class)
class CarUpNextTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val queue = listOf("a", "b", "c").map { Song(mediaId = it, title = "Track ${it.uppercase()}") }

    private fun playing(index: Int, repeatMode: RepeatMode = RepeatMode.Off, songs: List<Song> = queue) =
        PlaybackSnapshot(
            currentSong = songs.getOrNull(index),
            queue = songs,
            queueSize = songs.size,
            currentQueueIndex = index,
            repeatMode = repeatMode,
        )

    @Test
    fun the_next_item_follows_the_current_one() {
        assertEquals("b", playing(0).upNext()?.mediaId)
    }

    @Test
    fun the_last_item_has_nothing_next_unless_repeat_all_wraps() {
        assertNull(playing(2).upNext())
        assertNull(playing(2, RepeatMode.One).upNext())
        assertEquals("a", playing(2, RepeatMode.All).upNext()?.mediaId)
    }

    @Test
    fun a_single_track_or_empty_queue_has_nothing_next() {
        assertNull(playing(0, RepeatMode.All, songs = queue.take(1)).upNext())
        assertNull(playing(-1, songs = emptyList()).upNext())
    }

    @Test
    fun more_counts_the_tracks_after_the_next_one() {
        assertEquals(1, playing(0).moreAfterUpNext())
        assertEquals(0, playing(1).moreAfterUpNext())
        assertEquals(0, playing(2).moreAfterUpNext())
        // Repeat-all wraps to the first; everything but the current track and that one follows.
        assertEquals(1, playing(2, RepeatMode.All).moreAfterUpNext())
    }

    @Test
    fun tapping_up_next_opens_the_queue() {
        var queueOpened = 0
        render(playing(0)) { queueOpened++ }

        composeRule.onNodeWithText("UP NEXT").assertIsDisplayed()
        composeRule.onNodeWithText("Track B", substring = true).performClick()

        assertEquals(1, queueOpened)
    }

    @Test
    fun the_last_track_still_offers_the_queue() {
        var queueOpened = 0
        render(playing(2)) { queueOpened++ }

        composeRule.onNodeWithText("End of queue").performClick()

        assertEquals(1, queueOpened)
    }

    @Test
    fun an_empty_queue_shows_no_card() {
        render(playing(-1, songs = emptyList())) {}

        composeRule.onNodeWithText("UP NEXT").assertDoesNotExist()
    }

    private fun render(playback: PlaybackSnapshot, onQueueClick: () -> Unit) {
        composeRule.setContent {
            CarFullPlayerScreen(
                playback = playback,
                onCollapseClick = {},
                onPlayPauseClick = {},
                onSkipNextClick = {},
                onSkipPreviousClick = {},
                onShuffleClick = {},
                onRepeatClick = {},
                onSeek = {},
                onQueueClick = onQueueClick,
            )
        }
    }
}
