package com.example.nyasaplayer.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.core.playback.PlayerMode
import com.example.nyasaplayer.core.playback.PlayerUiState

private const val AnimationDurationMs = 300
private const val SpringDampingRatio = 0.8f
private const val SpringStiffness = 350f
private const val SlideOutDurationMs = 350

@Composable
fun GlobalPlayerLayer(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
    bottomOffset: Dp = 0.dp,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // MiniPlayer: persistent anchor, visible whenever player is active
        AnimatedVisibility(
            visible = state.playerMode != PlayerMode.Hidden,
            enter = fadeIn(tween(AnimationDurationMs)) + slideInVertically(tween(AnimationDurationMs)) { it },
            exit = fadeOut(tween(AnimationDurationMs)) + slideOutVertically(tween(AnimationDurationMs)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            MiniPlayer(
                state = state,
                onTogglePlayPause = onTogglePlayPause,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onExpand = onExpand,
                onDismiss = onDismiss,
                modifier = Modifier.padding(bottom = bottomOffset),
            )
        }

        // ExpandedPlayer: slides up over MiniPlayer, slides down to reveal it
        AnimatedVisibility(
            visible = state.playerMode == PlayerMode.Expanded,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = SpringDampingRatio, stiffness = SpringStiffness),
            ) { it },
            exit = slideOutVertically(
                animationSpec = tween(SlideOutDurationMs, easing = FastOutSlowInEasing),
            ) { it },
        ) {
            ExpandedPlayer(
                state = state,
                onTogglePlayPause = onTogglePlayPause,
                onSeek = onSeek,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onCollapse = onCollapse,
                onToggleRepeatMode = onToggleRepeatMode,
                onToggleLike = onToggleLike,
                onToggleShuffle = onToggleShuffle,
                onClearError = onClearError,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
