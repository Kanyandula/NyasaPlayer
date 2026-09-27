package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** T03: a Home tap reports its own section, which is what decides the "Playing from" label. */
@RunWith(RobolectricTestRunner::class)
class CarHomeSectionClickTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val recent = listOf(Song(mediaId = "r1", title = "Resume Me"), Song(mediaId = "r2", title = "Recent Two"))
    private val popular = listOf(Song(mediaId = "p1", title = "Chart Topper"))
    private val taps = mutableListOf<String>()

    private fun render() {
        composeRule.setContent {
            CarHomeScreen(
                recentlyPlayed = recent,
                popularSongs = popular,
                isLoading = false,
                errorMessage = null,
                onRecentClick = { songs, song -> taps += "recent:${song.mediaId}/${songs.size}" },
                onPopularClick = { songs, song -> taps += "popular:${song.mediaId}/${songs.size}" },
                onRetry = {},
                onBrowseClick = {},
            )
        }
    }

    @Test
    fun a_recent_row_reports_the_recent_section() {
        render()

        composeRule.onNodeWithText("Recent Two").performClick()

        assertEquals(listOf("recent:r2/2"), taps)
    }

    @Test
    fun a_popular_row_reports_the_popular_section() {
        render()

        composeRule.onNodeWithText("Chart Topper").performClick()

        assertEquals(listOf("popular:p1/1"), taps)
    }
}
