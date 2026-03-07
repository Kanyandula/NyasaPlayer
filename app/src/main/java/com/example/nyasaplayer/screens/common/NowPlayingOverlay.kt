package com.example.nyasaplayer.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

private const val ScrimAlpha = 0.5f

@Composable
fun NowPlayingOverlay(
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        content()
        if (isCurrentTrack) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(Color.Black.copy(alpha = ScrimAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedEqualizer(
                    animate = isPlaying,
                    barColor = Color.White,
                )
            }
        }
    }
}
