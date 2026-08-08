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
 * was introduced exactly that way.
 */
fun Modifier.carTouchTarget(): Modifier =
    this.defaultMinSize(minWidth = CarTouchTargetSize, minHeight = CarTouchTargetSize)
