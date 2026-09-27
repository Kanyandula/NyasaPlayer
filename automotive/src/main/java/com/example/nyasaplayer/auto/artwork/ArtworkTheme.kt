package com.example.nyasaplayer.auto.artwork

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import com.example.nyasaplayer.auto.ui.theme.CarAmbientBlue
import com.example.nyasaplayer.auto.ui.theme.CarAmbientPurple
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaGoldDim
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

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
        fullPlayerGlow = NyasaGoldDim.copy(alpha = FullPlayerGlowAlpha / MaxChannel.toFloat()).toArgb(),
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
 */
@ColorInt
fun conditionArtworkColor(
    @ColorInt seed: Int,
    alpha: Int,
    @ColorInt background: Int = NyasaBackground.toArgb(),
    @ColorInt ceiling: Int = CarRaised.toArgb(),
): Int {
    val limit = luminance(ceiling)
    fun tinted(towardBackground: Double) = withAlpha(mix(seed, background, towardBackground), alpha)
    fun fits(towardBackground: Double) = luminance(composite(tinted(towardBackground), background)) <= limit

    if (fits(0.0)) return tinted(0.0)
    // ponytail: bisection to within 1/1024 of the least blend that fits; a closed form needs the
    // sRGB curve inverted per channel, for no visible difference.
    var low = 0.0
    var high = 1.0
    repeat(BisectionSteps) {
        val mid = (low + high) / 2
        if (fits(mid)) high = mid else low = mid
    }
    return tinted(high)
}

/** WCAG 2 relative luminance of an opaque colour. */
internal fun luminance(@ColorInt color: Int): Double {
    fun linear(channel: Int): Double {
        val c = channel / MaxChannel.toDouble()
        return if (c <= SrgbLinearThreshold) c / SrgbLinearDivisor else ((c + SrgbOffset) / SrgbScale).pow(SrgbGamma)
    }
    return RedWeight * linear(red(color)) + GreenWeight * linear(green(color)) + BlueWeight * linear(blue(color))
}

/** [top] drawn over opaque [bottom], as the canvas blends it: per channel, in sRGB. */
@ColorInt
internal fun composite(@ColorInt top: Int, @ColorInt bottom: Int): Int {
    val a = alpha(top) / MaxChannel.toDouble()
    fun channel(of: (Int) -> Int) = (of(top) * a + of(bottom) * (1 - a)).roundToInt()
    return argb(MaxChannel, channel(::red), channel(::green), channel(::blue))
}

/**
 * The first seed with a hue worth showing. Near-black and near-grey swatches are skipped: they
 * would darken the glow to nothing or wash it grey, and the design's tints are better than either.
 */
private fun firstUsable(vararg seeds: Int?): Int? = seeds.firstOrNull { it != null && carriesHue(it) }

private fun carriesHue(@ColorInt color: Int): Boolean {
    val high = max(red(color), max(green(color), blue(color)))
    val low = min(red(color), min(green(color), blue(color)))
    val chroma = (high - low) / MaxChannel.toDouble()
    return high >= MinSeedBrightness && chroma >= MinSeedChroma
}

@ColorInt
private fun mix(@ColorInt from: Int, @ColorInt to: Int, amount: Double): Int {
    fun channel(of: (Int) -> Int) = (of(from) + (of(to) - of(from)) * amount).roundToInt()
    return argb(MaxChannel, channel(::red), channel(::green), channel(::blue))
}

@ColorInt
private fun withAlpha(@ColorInt color: Int, alpha: Int): Int = (color and RgbMask) or (alpha shl AlphaShift)

private fun alpha(color: Int) = color ushr AlphaShift
private fun red(color: Int) = (color shr RedShift) and ChannelMask
private fun green(color: Int) = (color shr GreenShift) and ChannelMask
private fun blue(color: Int) = color and ChannelMask
private fun argb(a: Int, r: Int, g: Int, b: Int) =
    (a shl AlphaShift) or (r shl RedShift) or (g shl GreenShift) or b

private const val MaxChannel = 255
private const val ChannelMask = 0xFF
private const val RgbMask = 0x00FFFFFF
private const val AlphaShift = 24
private const val RedShift = 16
private const val GreenShift = 8
private const val BisectionSteps = 10

/** Seeds dimmer than this (max channel) or greyer than this (chroma) carry no hue to follow. */
private const val MinSeedBrightness = 40
private const val MinSeedChroma = 0.15

// WCAG 2 sRGB linearisation and luminance weights.
private const val SrgbLinearThreshold = 0.04045
private const val SrgbLinearDivisor = 12.92
private const val SrgbOffset = 0.055
private const val SrgbScale = 1.055
private const val SrgbGamma = 2.4
private const val RedWeight = 0.2126
private const val GreenWeight = 0.7152
private const val BlueWeight = 0.0722
