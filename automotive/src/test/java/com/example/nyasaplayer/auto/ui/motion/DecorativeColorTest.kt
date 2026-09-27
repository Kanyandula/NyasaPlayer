package com.example.nyasaplayer.auto.ui.motion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.nyasaplayer.auto.artwork.AmbientPrimaryAlpha
import com.example.nyasaplayer.auto.artwork.conditionArtworkColor
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    /**
     * Between two artwork hues each held to CarRaised — red to blue, the worst pair — the sRGB ease
     * stays under the ceiling the whole way, where Compose's Oklab path does not.
     */
    @Test
    fun an_eased_hue_change_never_passes_the_ceiling() {
        val ceiling = CarRaised.luminance()
        // Single layers conditioned right up to the ceiling, as the full player's glow is.
        val red = Color(conditionArtworkColor(0xFFFF0000.toInt(), AmbientPrimaryAlpha))
        val blue = Color(conditionArtworkColor(0xFF0000FF.toInt(), AmbientPrimaryAlpha))
        fun rendered(c: Color) = c.compositeOver(NyasaBackground).luminance()

        val a = SrgbColorConverter.convertToVector(red)
        val b = SrgbColorConverter.convertToVector(blue)
        for (step in 0..Steps) {
            val t = step / Steps.toFloat()
            val mid = SrgbColorConverter.convertFromVector(
                AnimationVector4D(
                    a.v1 + (b.v1 - a.v1) * t,
                    a.v2 + (b.v2 - a.v2) * t,
                    a.v3 + (b.v3 - a.v3) * t,
                    a.v4 + (b.v4 - a.v4) * t,
                ),
            )
            assertTrue("sRGB step $t at L %.5f".format(rendered(mid)), rendered(mid) <= ceiling)
        }
        assertTrue("the Oklab midpoint would pass it", rendered(lerp(red, blue, 0.5f)) > ceiling)
    }

    private companion object {
        const val Steps = 20
    }
}
