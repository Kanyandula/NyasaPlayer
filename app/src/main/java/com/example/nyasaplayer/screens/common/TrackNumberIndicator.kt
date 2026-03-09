package com.example.nyasaplayer.screens.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.core.common.ui.components.AnimatedEqualizer
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextTertiary

@Composable
fun TrackNumberIndicator(
    trackNumber: Int,
    isCurrentTrack: Boolean,
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.width(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isCurrentTrack) {
            AnimatedEqualizer(animate = isAnimating)
        } else {
            Text(
                text = trackNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = NyasaTextTertiary,
            )
        }
    }
}
