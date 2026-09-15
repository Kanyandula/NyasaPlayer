package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Screen 13 — the queue overlay, where remove and clear are parked-only. */
@RunWith(RobolectricTestRunner::class)
class CarQueueScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val removed = mutableListOf<Int>()

    private fun queue(size: Int) =
        List(size) { Song(mediaId = "s$it", title = "Title s$it", artistName = "Artist") }

    /**
     * A9's sibling of the Downloads case, and the reason it was found: the queue overlay is
     * allowed while driving, so it is never evicted, and its remove confirmation used to survive
     * the transition with a live Remove button on it. Parked, open the confirmation; start
     * driving; the dialog must be gone and nothing removed (FR-2.5).
     *
     * Drop the `isDriving` key from the row's `remember` and this fails.
     */
    @Test
    fun `starting to drive closes an open remove confirmation`() {
        var driving by mutableStateOf(false)
        composeRule.setContent {
            CarQueueScreen(
                queue = queue(4),
                currentIndex = 0,
                isDriving = driving,
                maxItems = 21,
                onCloseClick = {},
                onSkipTo = {},
                onRemove = { removed += it },
                onClearQueue = {},
                isPlaying = true,
            )
        }

        // Row 0 is the current track, whose remove is always disabled; row 1 is removable.
        composeRule.onAllNodesWithContentDescription("Remove from queue")[1].performClick()
        composeRule.onNodeWithText("Remove from queue?").assertIsDisplayed()

        driving = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Remove from queue?").assertDoesNotExist()
        assertTrue(removed.isEmpty())
    }

    @Test
    fun `parked, confirming a removal addresses the real queue index`() {
        composeRule.setContent {
            CarQueueScreen(
                queue = queue(4),
                currentIndex = 0,
                isDriving = false,
                maxItems = 21,
                onCloseClick = {},
                onSkipTo = {},
                onRemove = { removed += it },
                onClearQueue = {},
                isPlaying = true,
            )
        }

        composeRule.onAllNodesWithContentDescription("Remove from queue")[2].performClick()
        composeRule.onNodeWithText("Remove").performClick()

        assertEquals(listOf(2), removed)
    }
}
