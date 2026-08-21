package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
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
 * A tap detector, because the thing that leaks is a tap. The first version consumed every pointer
 * change on the main pass, on the theory that children handle their touches first and only the
 * remainder is blocked. That is not what happens: it also swallowed drags, so the search sheet's
 * result list would not scroll on device and neither would the queue's. Consuming only what is
 * left unconsumed does not help either — both were checked on the emulator (T4).
 *
 * Deliberately not a no-op `clickable`, which would announce an interactive control that does
 * nothing (FR-2.6); `detectTapGestures` publishes no semantics.
 *
 * Drags do fall through to whatever is underneath. Nothing under a full-screen surface is visible
 * to drag, and an unscrollable sheet is the worse failure.
 */
fun Modifier.carConsumeTouches(): Modifier = this.pointerInput(Unit) {
    detectTapGestures { }
}
