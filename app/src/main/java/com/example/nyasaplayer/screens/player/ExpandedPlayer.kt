package com.example.nyasaplayer.screens.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.R
import com.example.nyasaplayer.core.common.ui.components.ErrorBanner
import com.example.nyasaplayer.core.common.ui.icons.LyricsIcon
import com.example.nyasaplayer.core.common.ui.icons.PauseIcon
import com.example.nyasaplayer.core.common.ui.icons.QueueMusicIcon
import com.example.nyasaplayer.core.common.ui.icons.RepeatIcon
import com.example.nyasaplayer.core.common.ui.icons.RepeatOneIcon
import com.example.nyasaplayer.core.common.ui.icons.ShuffleIcon
import com.example.nyasaplayer.core.common.ui.icons.SkipNextIcon
import com.example.nyasaplayer.core.common.ui.icons.SkipPreviousIcon
import com.example.nyasaplayer.core.common.ui.icons.VolumeIcon
import com.example.nyasaplayer.core.common.ui.theme.AppTheme
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface1
import com.example.nyasaplayer.core.common.util.formatDuration
import com.example.nyasaplayer.core.playback.PlayerUiState
import com.example.nyasaplayer.core.playback.RepeatMode
import com.example.nyasaplayer.ui.preview.PreviewPlayerStatePaused
import com.example.nyasaplayer.ui.preview.PreviewPlayerStatePlaying
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val CollapseThresholdDp = 100f
private const val MaxDragDp = 300f
private const val MaxAlphaReduction = 0.5f
private const val GradientAlpha = 0.8f
private const val CollapseAnimDurationMs = 300

@Composable
fun ExpandedPlayer(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCollapse: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dragOffset = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        dragOffset.snapTo(0f)
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            NyasaPrimary.copy(alpha = GradientAlpha),
            NyasaSurface1,
        ),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { dialog() }
            .collapseOnDragDown(dragOffset, onCollapse)
            .background(NyasaSurface1)
            .background(gradientBrush),
    ) {
        ExpandedPlayerContent(
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
        )
    }
}

@Composable
private fun Modifier.collapseOnDragDown(
    dragOffset: Animatable<Float, *>,
    onCollapse: () -> Unit,
): Modifier {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxDragPx = with(density) { MaxDragDp.dp.toPx() }
    val thresholdPx = with(density) { CollapseThresholdDp.dp.toPx() }
    val screenHeightPx = with(density) { 2000.dp.toPx() }

    return this
        .offset { IntOffset(0, dragOffset.value.roundToInt()) }
        .alpha(1f - (dragOffset.value / maxDragPx).coerceIn(0f, MaxAlphaReduction))
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (dragOffset.value > thresholdPx) {
                        scope.launch {
                            dragOffset.animateTo(
                                screenHeightPx,
                                tween(CollapseAnimDurationMs, easing = FastOutSlowInEasing),
                            )
                            onCollapse()
                        }
                    } else {
                        scope.launch {
                            dragOffset.animateTo(
                                0f,
                                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                            )
                        }
                    }
                },
                onDragCancel = {
                    scope.launch {
                        dragOffset.animateTo(
                            0f,
                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                        )
                    }
                },
            ) { _, dragAmount ->
                scope.launch {
                    dragOffset.snapTo((dragOffset.value + dragAmount).coerceIn(0f, maxDragPx))
                }
            }
        }
}

@Composable
private fun ExpandedPlayerContent(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCollapse: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        ExpandedPlayerToolbar(onCollapse = onCollapse)
        state.error?.takeIf { it.isPlaybackError }?.let { playerError ->
            Spacer(modifier = Modifier.height(8.dp))
            ErrorBanner(
                title = playerError.title,
                subtitle = playerError.message,
                onRetry = onClearError,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        ExpandedPlayerArtwork(state = state)
        Spacer(modifier = Modifier.height(32.dp))
        ExpandedPlayerSongInfo(state = state, onToggleLike = onToggleLike)
        Spacer(modifier = Modifier.height(24.dp))
        ExpandedPlayerScrubber(state = state, onSeek = onSeek)
        Spacer(modifier = Modifier.height(24.dp))
        ExpandedPlayerControls(
            state = state,
            onTogglePlayPause = onTogglePlayPause,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
            onToggleRepeatMode = onToggleRepeatMode,
            onToggleShuffle = onToggleShuffle,
        )
        Spacer(modifier = Modifier.weight(1f))
        ExpandedPlayerBottomBar()
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ExpandedPlayerToolbar(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.collapse),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = stringResource(R.string.now_playing).uppercase(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center),
        )
        IconButton(onClick = { }, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(R.string.share),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ExpandedPlayerArtwork(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "artworkScale",
    )

    AsyncImage(
        model = state.currentSong?.resolvedCoverUrl,
        contentDescription = state.currentSong?.title,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(300.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = NyasaPrimary.copy(alpha = 0.2f),
                spotColor = NyasaPrimary.copy(alpha = 0.2f),
            )
            .clip(RoundedCornerShape(16.dp)),
    )
}

@Composable
private fun ExpandedPlayerSongInfo(
    state: PlayerUiState,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.currentSong?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.currentSong?.resolvedArtistName.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(onClick = onToggleLike) {
            Icon(
                imageVector = if (state.isCurrentSongLiked) {
                    Icons.Default.Favorite
                } else {
                    Icons.Default.FavoriteBorder
                },
                contentDescription = stringResource(R.string.favorite),
                tint = if (state.isCurrentSongLiked) NyasaPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ExpandedPlayerScrubber(
    state: PlayerUiState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.durationMs > 0) {
        Column(modifier = modifier.fillMaxWidth()) {
            Slider(
                value = state.currentPositionMs.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..state.durationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = NyasaBackground,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDuration(state.currentPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDuration(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExpandedPlayerControls(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = ShuffleIcon,
                contentDescription = stringResource(R.string.shuffle),
                tint = if (state.isShuffled) {
                    NyasaPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = onSkipPrevious, enabled = state.hasPrevious) {
            Icon(
                imageVector = SkipPreviousIcon,
                contentDescription = stringResource(R.string.previous_track),
                tint = if (state.hasPrevious) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        ExpandedPlayPauseButton(state = state, onTogglePlayPause = onTogglePlayPause)
        IconButton(onClick = onSkipNext, enabled = state.hasNext) {
            Icon(
                imageVector = SkipNextIcon,
                contentDescription = stringResource(R.string.next_track),
                tint = if (state.hasNext) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = onToggleRepeatMode) {
            Icon(
                imageVector = if (state.repeatMode == RepeatMode.One) RepeatOneIcon else RepeatIcon,
                contentDescription = stringResource(
                    when (state.repeatMode) {
                        RepeatMode.Off -> R.string.repeat_off
                        RepeatMode.All -> R.string.repeat_all
                        RepeatMode.One -> R.string.repeat_one
                    },
                ),
                tint = if (state.repeatMode != RepeatMode.Off) {
                    NyasaPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ExpandedPlayPauseButton(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isBuffering) {
        CircularProgressIndicator(
            modifier = modifier.size(56.dp),
            color = Color.White,
        )
    } else {
        IconButton(
            onClick = onTogglePlayPause,
            modifier = modifier
                .size(64.dp)
                .background(color = Color.White, shape = CircleShape),
        ) {
            Icon(
                imageVector = if (state.isPlaying) PauseIcon else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (state.isPlaying) R.string.pause else R.string.play,
                ),
                tint = NyasaBackground,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun ExpandedPlayerBottomBar(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { }) {
            Icon(
                imageVector = VolumeIcon,
                contentDescription = stringResource(R.string.volume),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = LyricsIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.lyrics),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = QueueMusicIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.queue),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ExpandedPlayerPlayingPreview() {
    AppTheme {
        ExpandedPlayer(
            state = PreviewPlayerStatePlaying,
            onTogglePlayPause = {},
            onSeek = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onCollapse = {},
            onToggleRepeatMode = {},
            onToggleLike = {},
            onToggleShuffle = {},
            onClearError = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ExpandedPlayerPausedPreview() {
    AppTheme {
        ExpandedPlayer(
            state = PreviewPlayerStatePaused,
            onTogglePlayPause = {},
            onSeek = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onCollapse = {},
            onToggleRepeatMode = {},
            onToggleLike = {},
            onToggleShuffle = {},
            onClearError = {},
        )
    }
}
