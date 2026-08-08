package com.example.nyasaplayer.auto.ui.motion

import android.content.ContentResolver
import android.database.ContentObserver
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val DefaultAnimatorScale = 1f

/**
 * Whether the decorative layer — the ambient gradient, the rail's sliding pill — may animate.
 *
 * Two independent conditions, per `docs/aaos-DESIGN.md` §Motion: the vehicle must be parked,
 * and the platform must not have animations switched off.
 *
 * Gating on [isDistractionOptimized] is deliberate. `AAOS_DRIVING_STATE_TESTING.md` warns
 * against gating *restrictions* on that flag alone, because idling reports it true with only
 * NO_VIDEO set — but that warning is about over-refusing a driver's action, where the cost is
 * a legitimate action denied. Over-freezing decoration costs nothing, and there is no
 * restriction flag for "decorative animation" to gate on instead.
 */
fun decorativeMotionEnabled(isDistractionOptimized: Boolean, animatorScale: Float): Boolean =
    !isDistractionOptimized && animatorScale != 0f

/**
 * Observes `Settings.Global.ANIMATOR_DURATION_SCALE`.
 *
 * Observed rather than sampled once: the user can turn animations off while the app is
 * running, and a decorative layer that keeps moving afterwards is the bug this exists to
 * prevent.
 */
@Composable
fun rememberAnimatorDurationScale(): State<Float> {
    val resolver = LocalContext.current.contentResolver
    val scale = remember { mutableFloatStateOf(readAnimatorScale(resolver)) }
    val currentResolver by rememberUpdatedState(resolver)

    DisposableEffect(currentResolver) {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                scale.floatValue = readAnimatorScale(currentResolver)
            }
        }
        currentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { currentResolver.unregisterContentObserver(observer) }
    }
    return scale
}

private fun readAnimatorScale(resolver: ContentResolver): Float =
    Settings.Global.getFloat(
        resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        DefaultAnimatorScale,
    )
