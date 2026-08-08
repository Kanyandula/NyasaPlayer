package com.example.nyasaplayer.core.common.ui.theme

import androidx.compose.ui.graphics.Color

// Premium dark surface layers
val NyasaBackground = Color(0xFF0D0D0D)
val NyasaSurface1 = Color(0xFF121212)
val NyasaSurface2 = Color(0xFF1A1A1A)
val NyasaSurface3 = Color(0xFF242424)
val NyasaSurface4 = Color(0xFF2E2E2E)
val NyasaSurface5 = Color(0xFF383838)

// Primary purple accent
val NyasaPrimary = Color(0xFFA855F7)
val NyasaPrimaryDark = Color(0xFF7C3AED)

// --- Champagne gold: AAOS brand accent -------------------------------------
// Added for the AAOS design system. Mobile still uses NyasaPrimary; it migrates
// to gold in Project B. See docs/superpowers/specs/2026-08-02-aaos-foundation-restrictions-design.md 1.2
//
// Gold is a LIGHT colour. Never put white text on it — that measures 2.29:1.
// Always use NyasaOnGold for labels on a gold fill.
val NyasaGold = Color(0xFFC9A84C)
val NyasaGoldDim = Color(0xFF7A6428)
val NyasaGoldBright = Color(0xFFE0C169)
val NyasaOnGold = Color(0xFF0A0A0C)

// Semantic
val NyasaError = Color(0xFFEF4444)

// Text
val NyasaTextSecondary = Color(0xB3FFFFFF) // white 70%
val NyasaTextTertiary = Color(0x66FFFFFF) // white 40%
