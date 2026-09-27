package com.example.nyasaplayer.auto.ui.motion

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private const val DefaultAnimatorScale = 1f

/** Slow enough to read as the room's light changing, not as something to look at. */
internal const val DecorativeColorTransitionMs = 1_500

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

    DisposableEffect(resolver) {
        // Main-thread callback rather than the binder thread. Snapshot state is safe to write
        // from any thread, but the rest of the codebase marshals callback-driven updates onto
        // a known thread and there is no reason to be the exception.
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale.floatValue = readAnimatorScale(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale
}

/**
 * A decorative colour that eases to [target] when [animate] (from [decorativeMotionEnabled]) and
 * otherwise jumps straight to it: a parked update with animations off still lands, it just does not
 * move (NFR-6). While driving the artwork theme itself holds (D-T38.6), so this rarely has a change
 * to show then.
 *
 * Eased in sRGB, not the Oklab `animateColorAsState` uses. Both ends are held to the CarRaised
 * ceiling, and the canvas composites in sRGB, so a straight sRGB path can never be lighter than its
 * brighter end; an Oklab path between two safe colours (red to blue) peaks well over it midway.
 */
@Composable
fun animateDecorativeColor(target: Color, animate: Boolean): State<Color> = animateValueAsState(
    targetValue = target,
    typeConverter = SrgbColorConverter,
    animationSpec = if (animate) tween(DecorativeColorTransitionMs) else snap(),
    label = "decorative colour",
)

/** Colour as its sRGB channels, so interpolation runs in the space the canvas blends in. */
internal val SrgbColorConverter = TwoWayConverter<Color, AnimationVector4D>(
    convertToVector = { AnimationVector4D(it.red, it.green, it.blue, it.alpha) },
    convertFromVector = { v ->
        Color(
            red = v.v1.coerceIn(0f, 1f),
            green = v.v2.coerceIn(0f, 1f),
            blue = v.v3.coerceIn(0f, 1f),
            alpha = v.v4.coerceIn(0f, 1f),
        )
    },
)

private fun readAnimatorScale(resolver: ContentResolver): Float =
    Settings.Global.getFloat(
        resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        DefaultAnimatorScale,
    )
