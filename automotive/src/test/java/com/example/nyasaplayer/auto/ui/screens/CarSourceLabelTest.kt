package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.playback.PlaybackSnapshot
import com.example.nyasaplayer.core.playback.QueueOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** T03: the full player names where the queue came from, and names nothing when it came from nowhere. */
@RunWith(RobolectricTestRunner::class)
class CarSourceLabelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val song = Song(mediaId = "a", title = "Malawi Wa Lero", albumName = "Kalindula")

    private fun render(origin: QueueOrigin) {
        composeRule.setContent {
            CarFullPlayerScreen(
                playback = PlaybackSnapshot(currentSong = song, queueOrigin = origin),
                onCollapseClick = {},
                onPlayPauseClick = {},
                onSkipNextClick = {},
                onSkipPreviousClick = {},
                onShuffleClick = {},
                onRepeatClick = {},
                onSeek = {},
                isLiked = false,
            )
        }
    }

    @Test
    fun an_album_origin_is_named_as_an_album() {
        render(QueueOrigin.Album(id = "al1", name = "Greatest Hits"))

        composeRule.onNodeWithText("PLAYING FROM ALBUM").assertIsDisplayed()
        composeRule.onNodeWithText("Greatest Hits").assertIsDisplayed()
        composeRule.onNodeWithText("PLAYING FROM PLAYLIST").assertDoesNotExist()
    }

    @Test
    fun no_origin_shows_no_label() {
        render(QueueOrigin.None)

        composeRule.onNodeWithText("PLAYING FROM", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Now Playing").assertDoesNotExist()
    }

    @Test
    fun every_origin_maps_to_its_label() {
        assertEquals(SourceLabel("PLAYING FROM PLAYLIST", "Road Trip"), QueueOrigin.Playlist("p1", "Road Trip").sourceLabel())
        assertEquals(SourceLabel("PLAYING FROM ARTIST", "Lucius Banda"), QueueOrigin.Artist("ar1", "Lucius Banda").sourceLabel())
        assertEquals(SourceLabel("PLAYING FROM GENRE", "Afrobeat"), QueueOrigin.Genre("Afrobeat").sourceLabel())
        assertEquals(SourceLabel("PLAYING FROM SEARCH", "“banda”"), QueueOrigin.Search("banda").sourceLabel())
        assertEquals(SourceLabel("PLAYING FROM", "Favourites"), QueueOrigin.Favourites.sourceLabel())
        assertEquals(SourceLabel("PLAYING FROM", "Downloads"), QueueOrigin.Downloads.sourceLabel())
        assertEquals(SourceLabel("PLAYING FROM", "Recently Played"), QueueOrigin.RecentlyPlayed.sourceLabel())
        assertNull(QueueOrigin.None.sourceLabel())
    }

    @Test
    fun a_blank_name_is_no_label() {
        // A detail tapped before its title loaded, or an empty search, would otherwise print "PLAYING FROM ALBUM" over nothing.
        assertNull(QueueOrigin.Album("al1", "").sourceLabel())
        assertNull(QueueOrigin.Search(" ").sourceLabel())
        assertNull(QueueOrigin.Genre("").sourceLabel())
    }
}
