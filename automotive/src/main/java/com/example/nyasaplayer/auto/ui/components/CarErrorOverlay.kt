package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.theme.CarGradientOrange
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.core.common.ui.icons.WarningIcon
import com.example.nyasaplayer.core.common.ui.icons.WifiOffIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaError
import com.example.nyasaplayer.core.playback.PlayerError

private val IconCircleSize = 128.dp
private val IconSize = 64.dp

@Composable
fun CarErrorOverlay(
    error: PlayerError,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipNext: (() -> Unit)? = null,
) {
    CarModalScrim(onDismiss = onDismiss, modifier = modifier) {
        CarModalCard {
            ErrorIcon(isPlaybackError = error.isPlaybackError)
            Spacer(modifier = Modifier.height(32.dp))
            ErrorText(title = error.title, message = error.message)
            Spacer(modifier = Modifier.height(32.dp))
            ErrorActions(
                isRetryable = error.isRetryable,
                onDismiss = onDismiss,
                onRetry = onRetry,
                onSkipNext = onSkipNext,
            )
        }
    }
}

@Composable
private fun ErrorIcon(
    isPlaybackError: Boolean,
    modifier: Modifier = Modifier,
) {
    val icon = if (isPlaybackError) WarningIcon else WifiOffIcon
    Box(
        modifier = modifier
            .size(IconCircleSize)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(CarGradientOrange, NyasaError))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(IconSize))
    }
}

@Composable
private fun ErrorText(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = CarTextSecondary,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
        )
    }
}

/**
 * On [CarPillButton], which carries the 76dp touch target and the gold-label contrast rule. The old
 * hand-rolled boxes had neither (A8).
 */
@Composable
private fun ErrorActions(
    isRetryable: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSkipNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Same rule as Retry: only an error about the current item has anything to skip past.
    val skipNext = onSkipNext.takeIf { isRetryable }
    // Three pills do not fit one row of a half-width card at the car's label size: on the 1024x768
    // emulator they wrapped mid-word. Skip next and Retry share a row; Dismiss gets its own (A8).
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (skipNext != null) {
                CarPillButton(
                    label = "Skip next",
                    onClick = skipNext,
                    modifier = Modifier.weight(1f),
                    filled = false,
                )
            } else {
                CarPillButton(
                    label = "Dismiss",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    filled = false,
                )
            }
            if (isRetryable) {
                CarPillButton(label = "Retry", onClick = onRetry, modifier = Modifier.weight(1f))
            }
        }
        if (skipNext != null) {
            CarPillButton(
                label = "Dismiss",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                filled = false,
            )
        }
    }
}
