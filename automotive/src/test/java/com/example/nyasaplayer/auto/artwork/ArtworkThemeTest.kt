package com.example.nyasaplayer.auto.artwork

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.example.nyasaplayer.auto.ui.theme.CarAmbientBlue
import com.example.nyasaplayer.auto.ui.theme.CarAmbientPurple
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T06 / T38: the artwork supplies hue, the app keeps supplying luminance. Every conditioned tint,
 * at its calibrated alpha over the background it lands on, must be no lighter than CarRaised —
 * the surface secondary text is measured on at 7.4:1.
 */
class ArtworkThemeTest {

    private val ceiling = CarRaised.luminance()

    private val white = 0xFFFFFFFF.toInt()
    private val yellow = 0xFFFFE000.toInt()
    private val cyan = 0xFF00FFFF.toInt()
    private val neonGreen = 0xFF39FF14.toInt()
    private val deepBlue = 0xFF0A1E3C.toInt()

    private fun alphaOf(color: Int) = color ushr 24
    private fun renderedLuminance(tint: Int) = Color(tint).compositeOver(NyasaBackground).luminance()

    @Test
    fun the_default_theme_is_what_ships_today() {
        val theme = ArtworkThemeDefaults.theme

        assertEquals(CarAmbientBlue.toArgb(), theme.ambientPrimary)
        assertEquals(CarAmbientPurple.toArgb(), theme.ambientSecondary)
        assertEquals(0x64, alphaOf(theme.ambientPrimary))
        assertEquals(0x3C, alphaOf(theme.ambientSecondary))
        assertEquals(0x26, alphaOf(theme.fullPlayerGlow))
    }

    @Test
    fun bright_seeds_are_darkened_to_no_lighter_than_car_raised() {
        for (seed in listOf(white, yellow, cyan, neonGreen)) {
            for (alpha in listOf(AmbientPrimaryAlpha, AmbientSecondaryAlpha, FullPlayerGlowAlpha)) {
                val tint = conditionArtworkColor(seed, alpha)
                val l = renderedLuminance(tint)
                assertTrue("${hex(seed)} @ $alpha rendered at L %.5f, over %.5f".format(l, ceiling), l <= ceiling)
                assertEquals("alpha is kept", alpha, alphaOf(tint))
            }
        }
    }

    @Test
    fun an_unconditioned_white_glow_would_fail_the_ceiling() {
        // The case the ticket measured at 5.81:1: proof the clamp above is doing work.
        val raw = (white and 0x00FFFFFF) or (AmbientPrimaryAlpha shl 24)

        assertTrue(renderedLuminance(raw) > ceiling)
    }

    @Test
    fun a_seed_already_dark_enough_passes_through_unchanged() {
        val tint = conditionArtworkColor(deepBlue, AmbientPrimaryAlpha)

        assertEquals((deepBlue and 0x00FFFFFF) or (AmbientPrimaryAlpha shl 24), tint)
    }

    @Test
    fun conditioning_keeps_the_hue() {
        val tint = conditionArtworkColor(yellow, AmbientPrimaryAlpha)
        val r = (tint shr 16) and 0xFF
        val g = (tint shr 8) and 0xFF
        val b = tint and 0xFF

        // Still yellow, just darker: red and green lead, blue trails.
        assertTrue("r=$r g=$g b=$b", r > b && g > b)
    }

    @Test
    fun a_coloured_cover_themes_away_from_the_defaults() {
        val theme = artworkThemeFrom(ArtworkSwatches(vibrant = 0xFFE02020.toInt(), muted = 0xFF8A4A4A.toInt()))

        assertNotEquals(ArtworkThemeDefaults.theme, theme)
        val r = (theme.ambientPrimary shr 16) and 0xFF
        val b = theme.ambientPrimary and 0xFF
        assertTrue("primary follows the red cover", r > b)
        for (tint in listOf(theme.ambientPrimary, theme.ambientSecondary, theme.fullPlayerGlow)) {
            assertTrue(renderedLuminance(tint) <= ceiling)
        }
    }

    @Test
    fun missing_black_or_grey_artwork_falls_back_to_the_defaults() {
        assertEquals(ArtworkThemeDefaults.theme, artworkThemeFrom(ArtworkSwatches()))
        assertEquals(ArtworkThemeDefaults.theme, artworkThemeFrom(ArtworkSwatches(dominant = 0xFF050505.toInt())))
        assertEquals(
            ArtworkThemeDefaults.theme,
            artworkThemeFrom(ArtworkSwatches(muted = 0xFF808080.toInt(), dominant = white)),
        )
    }

    @Test
    fun one_usable_swatch_supplies_both_tints() {
        val theme = artworkThemeFrom(ArtworkSwatches(vibrant = cyan))

        // The same seed — not an invented second hue — conditioned as a pair.
        assertEquals(artworkThemeFromSeeds(cyan, cyan), theme)
    }

    @Test
    fun the_ambient_tints_stacked_on_each_other_stay_under_the_ceiling() {
        // The worst the two circles can do is overlap completely; the pair is held to that (T08).
        for (seed in listOf(white, yellow, cyan, neonGreen)) {
            val theme = artworkThemeFromSeeds(seed, seed)
            val stacked = Color(theme.ambientSecondary)
                .compositeOver(Color(theme.ambientPrimary).compositeOver(NyasaBackground))
                .luminance()
            assertTrue("${hex(seed)} stacked at L %.5f, over %.5f".format(stacked, ceiling), stacked <= ceiling)
        }
    }

    private fun hex(color: Int) = "#%08X".format(color)
}
