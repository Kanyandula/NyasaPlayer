package com.example.nyasaplayer.auto.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.nyasaplayer.auto.ui.theme.CarAmbientBlue
import com.example.nyasaplayer.auto.ui.theme.CarAmbientPurple

private const val DriftDurationMs = 24_000
private const val FrozenDriftFraction = 0.5f
private const val GradientRadiusFactor = 0.9f
private const val DriftTravelFactor = 0.25f

/**
 * The decorative ambient layer: two soft radial tints that drift slowly behind the content.
 *
 * [animate] must come from
 * [com.example.nyasaplayer.auto.ui.motion.decorativeMotionEnabled] — parked, and platform
 * animations on.
 *
 * **Frozen means stopped, not hidden.** When [animate] is false the gradients render at a
 * fixed mid-drift frame rather than disappearing, so the background does not visibly change
 * at the moment the vehicle starts moving. Swapping to a different composable, or skipping
 * the draw, would make the transition itself a distraction — which is the thing the rule is
 * trying to prevent.
 *
 * The hue is fixed. Following the current artwork was deferred in A2 (D4); when it lands it
 * changes the two colours below and nothing else.
 */
@Composable
fun CarAmbientBackground(
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val driven by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = DriftDurationMs),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambientDrift",
    )
    val drift = if (animate) driven else FrozenDriftFraction

    Canvas(modifier = modifier.fillMaxSize()) {
        val travel = size.height * DriftTravelFactor
        val radius = size.minDimension * GradientRadiusFactor

        drawCircleTint(
            color = CarAmbientBlue,
            center = Offset(x = size.width * 0.15f, y = travel * drift),
            radius = radius,
        )
        drawCircleTint(
            color = CarAmbientPurple,
            center = Offset(x = size.width * 0.85f, y = size.height - travel * drift),
            radius = radius,
        )
    }
}

private fun DrawScope.drawCircleTint(
    color: Color,
    center: Offset,
    radius: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
