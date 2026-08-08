package com.example.nyasaplayer.core.common.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Guards the brand accent against the white-on-gold mistake.
 *
 * Gold is a light colour. White text on it measures 2.29:1, which is unusable.
 * [NyasaOnGold] exists so nobody reaches for Color.White out of habit.
 */
class BrandContrastTest {

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= LOW_CHANNEL_CUTOFF) v / LOW_CHANNEL_DIVISOR else ((v + OFFSET) / SCALE).pow(GAMMA)
    }

    private fun luminance(color: Color): Double =
        R_WEIGHT * channel(color.red) + G_WEIGHT * channel(color.green) + B_WEIGHT * channel(color.blue)

    private fun contrast(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + AMBIENT) / (minOf(a, b) + AMBIENT)
    }

    @Test
    fun onGold_overGold_meetsAaaForNormalText() {
        assertTrue(
            "NyasaOnGold on NyasaGold must be >= 7:1",
            contrast(NyasaOnGold, NyasaGold) >= AAA_NORMAL,
        )
    }

    @Test
    fun white_overGold_failsAa_whichIsWhyOnGoldExists() {
        assertTrue(
            "White on gold must fail AA — if this passes, the gold token changed",
            contrast(Color.White, NyasaGold) < AA_NORMAL,
        )
    }

    @Test
    fun onGold_overGoldBright_meetsAaaForNormalText() {
        assertTrue(
            "NyasaOnGold on NyasaGoldBright must be >= 7:1",
            contrast(NyasaOnGold, NyasaGoldBright) >= AAA_NORMAL,
        )
    }

    private companion object {
        const val LOW_CHANNEL_CUTOFF = 0.03928
        const val LOW_CHANNEL_DIVISOR = 12.92
        const val OFFSET = 0.055
        const val SCALE = 1.055
        const val GAMMA = 2.4
        const val R_WEIGHT = 0.2126
        const val G_WEIGHT = 0.7152
        const val B_WEIGHT = 0.0722
        const val AMBIENT = 0.05
        const val AAA_NORMAL = 7.0
        const val AA_NORMAL = 4.5
    }
}
