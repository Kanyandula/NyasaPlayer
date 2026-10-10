package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.playback.PlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The progress block's width is capped, not fixed (D75).
 *
 * Measured from the elapsed time's left edge to the heart's, so it reads layout only and not
 * Robolectric's font metrics. `weight(1f).widthIn(max)` looks like the same cap and never binds,
 * which the 1440dp case catches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1440dp-h800dp")
class CarMiniPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `beside the rail on a 1440dp unit the progress block stops at its cap`() {
        // 1440 - 176 rail: the share would be ~418dp, so the cap binds.
        assertEquals(300f, progressBlockWidth(barWidth = 1264.dp).value, 0.5f)
    }

    @Test
    fun `on a 1024dp unit the cap does not bind and the block keeps its equal share`() {
        // 1024 - 176 rail, less 48dp of padding and 380dp of controls, halved.
        assertEquals(210f, progressBlockWidth(barWidth = 848.dp).value, 0.5f)
    }

    private fun progressBlockWidth(barWidth: Dp): Dp {
        composeRule.setContent {
            CarMiniPlayer(
                playback = PlaybackSnapshot(
                    currentSong = Song(mediaId = "a", title = "Track A", durationMs = 227_000L),
                    currentPositionMs = 94_000L,
                    durationMs = 227_000L,
                ),
                onTogglePlayPause = {},
                onSkipNext = {},
                onSkipPrevious = {},
                onExpand = {},
                modifier = Modifier.width(barWidth),
            )
        }
        // Unmerged: the bar is one clickable, so the merged tree folds the time into the whole bar.
        val elapsedLeft = composeRule.onNodeWithText("1:34", useUnmergedTree = true).getBoundsInRoot().left
        val heartLeft = composeRule.onNodeWithContentDescription("Like").getBoundsInRoot().left
        // The block's 24dp start padding sits before the elapsed time.
        return heartLeft - elapsedLeft + ProgressStartPadding
    }

    private companion object {
        val ProgressStartPadding = 24.dp
    }
}
