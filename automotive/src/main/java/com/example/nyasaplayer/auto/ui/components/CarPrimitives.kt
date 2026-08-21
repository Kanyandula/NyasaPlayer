package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * Swallows touches that would otherwise reach whatever is drawn underneath.
 *
 * Apply to the root of every full-screen surface stacked above the shell. Drawing over
 * `BrowseShell` does not stop Compose delivering touches to it: on device (T6), tapping an empty
 * area of the open search sheet pressed the nav rail button behind it and switched tabs, and the
 * queue leaked the same way through the strip beside its rows.
 *
 * Consumed on the main pass, so the surface's own children still handle their touches first —
 * only what they leave unhandled is blocked. Deliberately not a no-op `clickable`, which would
 * announce an interactive control that does nothing (FR-2.6).
 */
fun Modifier.carConsumeTouches(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}
