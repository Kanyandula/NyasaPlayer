package com.example.nyasaplayer.screens.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.nyasaplayer.R
import com.example.nyasaplayer.player.PlayerUiState
import com.example.nyasaplayer.ui.icons.PauseIcon
import com.example.nyasaplayer.ui.preview.PreviewPlayerStateMini
import com.example.nyasaplayer.ui.theme.AppTheme
import com.example.nyasaplayer.ui.theme.NyasaPrimary
import com.example.nyasaplayer.ui.theme.NyasaSurface2
import com.example.nyasaplayer.ui.theme.NyasaSurface4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val currentOnDismiss = rememberUpdatedState(onDismiss)

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            currentOnDismiss.value()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        modifier = modifier,
    ) {
        MiniPlayerContent(
            state = state,
            onTogglePlayPause = onTogglePlayPause,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
            onExpand = onExpand,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun MiniPlayerContent(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NyasaSurface2)
            .clickable(onClick = onExpand),
    ) {
        MiniPlayerRow(
            state = state,
            onTogglePlayPause = onTogglePlayPause,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
            onDismiss = onDismiss,
        )
        if (state.durationMs > 0) {
            LinearProgressIndicator(
                progress = {
                    (state.currentPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(horizontal = 12.dp),
                color = NyasaPrimary,
                trackColor = NyasaSurface4,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun MiniPlayerPlayingPreview() {
    AppTheme {
        MiniPlayerContent(
            state = PreviewPlayerStateMini,
            onTogglePlayPause = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onExpand = {},
            onDismiss = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun MiniPlayerPausedPreview() {
    AppTheme {
        MiniPlayerContent(
            state = PreviewPlayerStateMini.copy(isPlaying = false),
            onTogglePlayPause = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onExpand = {},
            onDismiss = {},
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniPlayerSongInfo(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = state.currentSong?.resolvedCoverUrl,
            contentDescription = state.currentSong?.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = state.currentSong?.title.orEmpty(),
                modifier = Modifier.basicMarquee(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
            )
            Text(
                text = state.currentSong?.resolvedArtistName.orEmpty(),
                modifier = Modifier.basicMarquee(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MiniPlayerRow(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniPlayerSongInfo(
            state = state,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSkipPrevious, enabled = state.hasPrevious) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_media_previous),
                contentDescription = stringResource(R.string.previous_track),
                tint = Color.White,
            )
        }
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .size(36.dp)
                .background(color = NyasaPrimary, shape = CircleShape),
        ) {
            Icon(
                imageVector = if (state.isPlaying) PauseIcon else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (state.isPlaying) R.string.pause else R.string.play,
                ),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onSkipNext, enabled = state.hasNext) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_media_next),
                contentDescription = stringResource(R.string.next_track),
                tint = Color.White,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
                tint = Color.White,
            )
        }
    }
}
