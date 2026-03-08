package com.example.nyasaplayer.screens.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.core.common.ui.theme.AppTheme
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary

private const val BarCount = 3
private const val Bar1DurationMs = 450
private const val Bar2DurationMs = 550
private const val Bar3DurationMs = 400
private const val Bar1MinFraction = 0.2f
private const val Bar1MaxFraction = 0.9f
private const val Bar2MinFraction = 0.3f
private const val Bar2MaxFraction = 1.0f
private const val Bar3MinFraction = 0.15f
private const val Bar3MaxFraction = 0.75f

@Composable
fun AnimatedEqualizer(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    barColor: Color = NyasaPrimary,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 16.dp,
    spacing: Dp = 2.dp,
) {
    val fractions = if (animate) {
        val transition = rememberInfiniteTransition(label = "equalizer")
        val bar1 by transition.animateFloat(
            initialValue = Bar1MinFraction,
            targetValue = Bar1MaxFraction,
            animationSpec = infiniteRepeatable(tween(Bar1DurationMs), RepeatMode.Reverse),
            label = "bar1",
        )
        val bar2 by transition.animateFloat(
            initialValue = Bar2MinFraction,
            targetValue = Bar2MaxFraction,
            animationSpec = infiniteRepeatable(tween(Bar2DurationMs), RepeatMode.Reverse),
            label = "bar2",
        )
        val bar3 by transition.animateFloat(
            initialValue = Bar3MinFraction,
            targetValue = Bar3MaxFraction,
            animationSpec = infiniteRepeatable(tween(Bar3DurationMs), RepeatMode.Reverse),
            label = "bar3",
        )
        listOf(bar1, bar2, bar3)
    } else {
        null
    }

    if (fractions != null) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.Bottom,
        ) {
            repeat(BarCount) { index ->
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(maxHeight * fractions[index])
                        .clip(RoundedCornerShape(barWidth / 2))
                        .background(barColor),
                )
            }
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(BarCount) {
                Box(
                    modifier = Modifier
                        .size(barWidth)
                        .clip(CircleShape)
                        .background(barColor),
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun AnimatedEqualizerPreview() {
    AppTheme {
        AnimatedEqualizer()
    }
}
