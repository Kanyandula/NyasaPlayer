package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.auto.ui.theme.CarMiniPlayerProgressMaxWidth
import com.example.nyasaplayer.auto.ui.theme.CarNavRailWidth
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
 * Measured between node edges — the art, the elapsed time and the previous button — so it reads
 * layout only and not Robolectric's font metrics. `weight(1f).widthIn(max)` looks like the same cap and never binds,
 * which the 1440dp case catches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1440dp-h800dp")
class CarMiniPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `beside the rail on a 1440dp unit the progress block stops at its cap`() {
        // The share would be ~468dp, so the cap binds.
        val (_, progress) = blockWidths(barWidth = 1440.dp - CarNavRailWidth)
        assertEquals(CarMiniPlayerProgressMaxWidth.value, progress.value, 0.5f)
    }

    @Test
    fun `on a 1024dp unit the cap does not bind and the block keeps its equal share`() {
        // ~260dp each: an equal split, which a fixed width would break.
        val (identity, progress) = blockWidths(barWidth = 1024.dp - CarNavRailWidth)
        assertEquals(identity.value, progress.value, 0.5f)
    }

    /** The title-and-artist block's width, then the progress block's. */
    private fun blockWidths(barWidth: Dp): Pair<Dp, Dp> {
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
        // Unmerged: the bar is one clickable, so the merged tree folds its children into the bar.
        val artLeft = composeRule.onNodeWithContentDescription("Track A", useUnmergedTree = true)
            .getBoundsInRoot().left
        // The block's start padding sits before the elapsed time.
        val progressLeft = composeRule.onNodeWithText("1:34", useUnmergedTree = true)
            .getBoundsInRoot().left - ProgressStartPadding
        val transportLeft = composeRule.onNodeWithContentDescription("Previous").getBoundsInRoot().left
        return (progressLeft - artLeft) to (transportLeft - progressLeft)
    }
}

private val ProgressStartPadding = 12.dp
