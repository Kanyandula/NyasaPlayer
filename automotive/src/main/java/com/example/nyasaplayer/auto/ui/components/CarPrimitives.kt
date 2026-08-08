package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize

/**
 * Enforces the minimum touch target on any interactive element.
 *
 * The glyph keeps its visual size; this supplies the target around it. Apply to every
 * clickable whose drawn size is smaller than [CarTouchTargetSize] — icon buttons,
 * hearts, transport controls, chevrons.
 *
 * Do not remove it to tighten spacing. Every sub-minimum target in the HTML prototype
 * (docs/aaos-screens.html) was introduced exactly that way.
 *
 * Existing code often writes `Modifier.size(CarTouchTargetSize)` instead, which pins an
 * exact box. Prefer this modifier where the target is a floor and the content may be
 * larger; there is no need to migrate the existing exact-size call sites.
 */
fun Modifier.carTouchTarget(): Modifier =
    this.defaultMinSize(minWidth = CarTouchTargetSize, minHeight = CarTouchTargetSize)
