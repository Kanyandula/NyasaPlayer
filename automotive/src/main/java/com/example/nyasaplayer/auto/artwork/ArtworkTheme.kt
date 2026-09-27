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
    return artworkThemeFromSeeds(primary, secondary)
}

/**
 * The two ambient tints are conditioned together, as if the secondary sat right on the primary's
 * centre: where the circles overlap their light adds, and one tint each at the ceiling measured over
 * it at the drift's lowest frame (T08). Stacking is the worst any window shape can do, so the pair
 * holds on all of them. The full player's glow is a single layer and keeps its own ceiling.
 */
internal fun artworkThemeFromSeeds(@ColorInt primary: Int, @ColorInt secondary: Int): ArtworkTheme {
    val (ambientPrimary, ambientSecondary) =
        conditionArtworkLayers(listOf(primary to AmbientPrimaryAlpha, secondary to AmbientSecondaryAlpha))
    return ArtworkTheme(
        ambientPrimary = ambientPrimary,
        ambientSecondary = ambientSecondary,
        fullPlayerGlow = conditionArtworkColor(primary, FullPlayerGlowAlpha),
    )
}

/**
 * [seed] at [alpha], blended toward [background] only as far as it takes to composite no lighter
 * than [ceiling] — so bright and neon covers keep their hue but lose their light. Palette's raw
 * colour never reaches the screen; this is the only way through.
 */
@ColorInt
fun conditionArtworkColor(
    @ColorInt seed: Int,
    alpha: Int,
    @ColorInt background: Int = NyasaBackground.toArgb(),
    @ColorInt ceiling: Int = CarRaised.toArgb(),
): Int = conditionArtworkLayers(listOf(seed to alpha), background, ceiling).single()

/**
 * Each (seed, alpha) layer, drawn in order over [background], blended toward it by one shared amount
 * — the least that keeps the whole stack no lighter than [ceiling]. Sharing it keeps the layers'
 * balance: no one hue is sacrificed to let another stay bright.
 */
internal fun conditionArtworkLayers(
    layers: List<Pair<Int, Int>>,
    @ColorInt background: Int = NyasaBackground.toArgb(),
    @ColorInt ceiling: Int = CarRaised.toArgb(),
): List<Int> {
    val base = Color(background)
    val limit = Color(ceiling).luminance()
    fun tinted(towardBackground: Float) = layers.map { (seed, alpha) ->
        mixSrgb(Color(seed).copy(alpha = 1f), base, towardBackground).copy(alpha = alpha / MaxChannel)
    }
    fun fits(towardBackground: Float) =
        tinted(towardBackground).fold(base) { under, layer -> layer.compositeOver(under) }.luminance() <= limit

    if (fits(0f)) return tinted(0f).map { it.toArgb() }
    // ponytail: bisection to within 1/1024 of the least blend that fits; a closed form needs the
    // sRGB curve inverted per channel, for no visible difference.
    var low = 0f
    var high = 1f
    repeat(BisectionSteps) {
        val mid = (low + high) / 2
        if (fits(mid)) high = mid else low = mid
    }
    return tinted(high).map { it.toArgb() }
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
