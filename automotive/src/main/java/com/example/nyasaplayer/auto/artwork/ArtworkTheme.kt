package com.example.nyasaplayer.auto.artwork

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.example.nyasaplayer.auto.ui.theme.CarAmbientBlue
import com.example.nyasaplayer.auto.ui.theme.CarAmbientPurple
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaGoldDim

/**
 * The colours the car paints behind content: the ambient layer's two tints and the full player's
 * glow. ARGB ints, not Compose colours, so the state holding it stays plain data (T38).
 */
data class ArtworkTheme(
    @ColorInt val ambientPrimary: Int,
    @ColorInt val ambientSecondary: Int,
    @ColorInt val fullPlayerGlow: Int,
)

object ArtworkThemeDefaults {

    /** What ships today, and what any cover that cannot supply a hue falls back to. */
    val theme = ArtworkTheme(
        ambientPrimary = CarAmbientBlue.toArgb(),
        ambientSecondary = CarAmbientPurple.toArgb(),
        fullPlayerGlow = NyasaGoldDim.copy(alpha = FullPlayerGlowAlpha / MaxChannel).toArgb(),
    )
}

/**
 * The alphas the tints were calibrated at (`AutomotiveColors.kt`). They stay fixed: the artwork
 * supplies hue, never strength.
 */
internal const val AmbientPrimaryAlpha = 0x64
internal const val AmbientSecondaryAlpha = 0x3C
internal const val FullPlayerGlowAlpha = 0x26

/**
 * The five swatches Palette offers, in ARGB, each null when the cover has none. A plain holder so
 * seed selection is testable without a bitmap.
 */
data class ArtworkSwatches(
    @ColorInt val vibrant: Int? = null,
    @ColorInt val muted: Int? = null,
    @ColorInt val darkVibrant: Int? = null,
    @ColorInt val darkMuted: Int? = null,
    @ColorInt val dominant: Int? = null,
)

/**
 * The theme for a cover's swatches: a hue from the artwork at the calibrated strength, darkened
 * until it composites no lighter than [CarRaised] (D-T38.3), or [ArtworkThemeDefaults] when the
 * cover offers no usable hue at all (D-T38.4).
 */
fun artworkThemeFrom(swatches: ArtworkSwatches): ArtworkTheme {
    val primary = with(swatches) { firstUsable(vibrant, muted, darkVibrant, darkMuted, dominant) }
        ?: return ArtworkThemeDefaults.theme
    // Falls back to the primary hue, not an invented one: depth comes from placement and alpha.
    val secondary = with(swatches) { firstUsable(muted, darkMuted, darkVibrant, vibrant, dominant) } ?: primary
    return ArtworkTheme(
        ambientPrimary = conditionArtworkColor(primary, AmbientPrimaryAlpha),
        ambientSecondary = conditionArtworkColor(secondary, AmbientSecondaryAlpha),
        fullPlayerGlow = conditionArtworkColor(primary, FullPlayerGlowAlpha),
    )
}

/**
 * [seed] at [alpha], blended toward [background] only as far as it takes to composite no lighter
 * than [ceiling] — so bright and neon covers keep their hue but lose their light. Palette's raw
 * colour never reaches the screen; this is the only way through.
 *
 * Per tint, over the background it lands on. That is exact for the full player's single glow; where
 * the ambient layer's two circles overlap they can add up past one centre on a near-square window,
 * which the pixel test on the rendered layer (T08) is what catches.
 */
@ColorInt
fun conditionArtworkColor(
    @ColorInt seed: Int,
    alpha: Int,
    @ColorInt background: Int = NyasaBackground.toArgb(),
    @ColorInt ceiling: Int = CarRaised.toArgb(),
): Int {
    val base = Color(background)
    val limit = Color(ceiling).luminance()
    val opaque = Color(seed).copy(alpha = 1f)
    fun tinted(towardBackground: Float) = mixSrgb(opaque, base, towardBackground).copy(alpha = alpha / MaxChannel)
    fun fits(towardBackground: Float) = tinted(towardBackground).compositeOver(base).luminance() <= limit

    if (fits(0f)) return tinted(0f).toArgb()
    // ponytail: bisection to within 1/1024 of the least blend that fits; a closed form needs the
    // sRGB curve inverted per channel, for no visible difference.
    var low = 0f
    var high = 1f
    repeat(BisectionSteps) {
        val mid = (low + high) / 2
        if (fits(mid)) high = mid else low = mid
    }
    return tinted(high).toArgb()
}

/**
 * The first seed with a hue worth showing. Near-black and near-grey swatches are skipped: they
 * would darken the glow to nothing or wash it grey, and the design's tints are better than either.
 */
private fun firstUsable(vararg seeds: Int?): Int? = seeds.firstOrNull { it != null && carriesHue(Color(it)) }

private fun carriesHue(color: Color): Boolean {
    val high = maxOf(color.red, color.green, color.blue)
    val low = minOf(color.red, color.green, color.blue)
    return high >= MinSeedBrightness && high - low >= MinSeedChroma
}

/** A straight blend in sRGB, as the canvas composites; Compose's `lerp` works in Oklab instead. */
private fun mixSrgb(from: Color, to: Color, amount: Float) = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
)

private const val MaxChannel = 255f
private const val BisectionSteps = 10

/** Seeds dimmer than this (brightest channel) or greyer than this (chroma) carry no hue to follow. */
private const val MinSeedBrightness = 40 / MaxChannel
private const val MinSeedChroma = 0.15f
