package com.example.nyasaplayer.auto.ui.theme

import androidx.compose.ui.graphics.Color

// Brand accent lives in :core:common as NyasaGold / NyasaOnGold. Use those directly.
// The surfaces below are car-only — mobile has no obsidian surface.

/** Base background, edge to edge on every screen. */
val CarObsidian = Color(0xFF0A0A0C)

/** System bar and navigation rail. */
val CarChrome = Color(0xFF111118)

/** Cards and the mini-player. */
val CarGlass = Color(0xFF181824)

/** Elevated cards, inputs, chips. */
val CarRaised = Color(0xFF1E1E2A)

/**
 * Metadata and secondary labels.
 *
 * Do not darken without re-measuring. This was #A0A0B0 and gave only 6.8:1 on cards —
 * AA, not AAA. The binding surface is [CarRaised] at 7.4:1, NOT [CarObsidian] at 8.8:1,
 * so measuring against the base gives a false pass.
 */
val CarTextSecondary = Color(0xFFACACBC)

/** Disabled labels. Exempt from contrast minimums. */
val CarTextDisabled = Color(0xFF555568)

/**
 * Dimming behind a modal. One value, because three had already drifted apart (0.8, 0.8,
 * 0.74) before this token existed.
 */
val CarScrim = Color(0xCC000000)

/** Outline on an unselected chip, pill or tab. */
val CarOutline = Color(0x1FFFFFFF)

/**
 * Hairline divider between chrome regions — the mini-player's top border.
 *
 * 8% white per the design, deliberately lighter than [CarOutline]'s 12%: that one outlines
 * an interactive control, this one separates two surfaces.
 */
val CarDivider = Color(0x14FFFFFF)

/**
 * Ambient background tints. Never used as a fill on an interactive element.
 *
 * Unused until the ambient-motion work; screens currently draw NyasaBackground. Same for
 * [CarObsidian] and [CarTextDisabled] — declared here so the whole surface ramp is defined
 * in one place rather than arriving piecemeal.
 */
val CarAmbientBlue = Color(0xFF1A3A5C)
val CarAmbientPurple = Color(0x4D643CB4)

// Gradient pairs shared across AAOS screens. These are content artwork gradients,
// not brand — they stay as they are.

// Pink → Dark Red  (Home: Radio, Browse: Trending Now)
val CarGradientPink = Color(0xFFEC4899)
val CarGradientRose = Color(0xFFBE123C)

// Red → Dark Red  (Home: Favorites)
val CarGradientRed = Color(0xFFEF4444)
val CarGradientRedDark = Color(0xFFB91C1C)

// Blue → Indigo  (Home: Trending)
val CarGradientBlue = Color(0xFF3B82F6)
val CarGradientIndigo = Color(0xFF4338CA)

// Blue → Cyan  (Browse: New Releases)
val CarGradientBlueCyan = Color(0xFF06B6D4)

// Green → Dark Green  (Browse: Top Charts)
val CarGradientGreen = Color(0xFF22C55E)
val CarGradientGreenDark = Color(0xFF059669)

// Orange → Dark Orange  (Browse: Genres, Error icon start)
val CarGradientOrange = Color(0xFFF97316)
val CarGradientOrangeDark = Color(0xFFD97706)

// Indigo → Dark Indigo  (Browse: Podcasts)
val CarGradientIndigoPurple = Color(0xFF6366F1)
