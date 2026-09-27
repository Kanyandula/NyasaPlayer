package com.example.nyasaplayer.auto.ui.motion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * T08: an artwork hue eases in only when decorative motion is allowed. With motion off — driving, or
 * animations switched off — a new colour still lands, at once.
 */
@RunWith(RobolectricTestRunner::class)
class DecorativeColorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val from = Color(0x641A3A5C)
    private val to = Color(0x64603010)

    private var target by mutableStateOf(from)
    private var shown = from

    private fun render(animate: Boolean) {
        composeRule.setContent { shown = animateDecorativeColor(target, animate).value }
        composeRule.waitForIdle()
    }

    @Test
    fun with_motion_the_colour_eases_toward_the_new_hue() {
        render(animate = true)
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnUiThread { target = to }
        composeRule.waitForIdle()
        // A third of the way in: early frames round away, as sRGB colours hold 8 bits a channel.
        composeRule.mainClock.advanceTimeBy(DecorativeColorTransitionMs.toLong() / 3)
        assertNotEquals("still mid-transition", to, shown)
        assertNotEquals("but under way", from, shown)

        composeRule.mainClock.advanceTimeBy(DecorativeColorTransitionMs.toLong() * 2)
        assertEquals(to, shown)
    }

    @Test
    fun without_motion_the_new_hue_lands_at_once() {
        render(animate = false)
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnUiThread { target = to }
        composeRule.waitForIdle()
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }

        assertEquals(to, shown)
    }
}
