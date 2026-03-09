package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.core.common.ui.icons.PauseIcon
import com.example.nyasaplayer.core.common.ui.icons.SkipNextIcon
import com.example.nyasaplayer.core.common.ui.icons.SkipPreviousIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.util.formatDuration
import com.example.nyasaplayer.core.playback.PlaybackSnapshot

private val MiniPlayerHeight = 112.dp
private val ArtSize = 80.dp
private val PlayButtonSize = 80.dp
private val ControlButtonSize = 76.dp
private val LikeButtonSize = 76.dp

@Suppress("LongParameterList")
@Composable
fun CarMiniPlayer(
    playback: PlaybackSnapshot,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    onLikeClick: () -> Unit = {},
) {
    val song = playback.currentSong ?: return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .background(NyasaSurface2)
            .clickable(onClick = onExpand)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NowPlayingInfo(
            title = song.title,
            artist = song.resolvedArtistName,
            coverUrl = song.resolvedCoverUrl,
            modifier = Modifier.weight(1f),
        )
        MiniPlayerControls(
            isPlaying = playback.isPlaying,
            onTogglePlayPause = onTogglePlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
        )
        ProgressSection(
            currentPositionMs = playback.currentPositionMs,
            durationMs = playback.durationMs,
            isLiked = isLiked,
            onLikeClick = onLikeClick,
            modifier = Modifier
                .weight(1f)
                .padding(start = 24.dp),
        )
    }
}

@Composable
private fun NowPlayingInfo(
    title: String,
    artist: String,
    coverUrl: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(ArtSize)
                .clip(RoundedCornerShape(12.dp)),
        )
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                color = NyasaTextSecondary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MiniPlayerControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onSkipPrevious,
            modifier = Modifier
                .size(ControlButtonSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Icon(SkipPreviousIcon, "Previous", tint = Color.White, modifier = Modifier.size(24.dp))
        }

        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .size(PlayButtonSize)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark))),
        ) {
            Icon(
                imageVector = if (isPlaying) PauseIcon else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }

        IconButton(
            onClick = onSkipNext,
            modifier = Modifier
                .size(ControlButtonSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Icon(SkipNextIcon, "Next", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ProgressSection(
    currentPositionMs: Long,
    durationMs: Long,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(formatDuration(currentPositionMs), color = NyasaTextSecondary, fontSize = 14.sp)

        val progress = if (durationMs > 0) {
            (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = NyasaPrimary,
            trackColor = Color.White.copy(alpha = 0.2f),
        )

        Text(formatDuration(durationMs), color = NyasaTextSecondary, fontSize = 14.sp)

        IconButton(
            onClick = onLikeClick,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(LikeButtonSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isLiked) "Unlike" else "Like",
                tint = if (isLiked) NyasaPrimary else NyasaTextSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
