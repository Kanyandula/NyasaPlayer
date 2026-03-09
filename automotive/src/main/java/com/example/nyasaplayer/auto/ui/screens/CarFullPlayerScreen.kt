package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.core.common.ui.icons.MoreHorizIcon
import com.example.nyasaplayer.core.common.ui.icons.PauseIcon
import com.example.nyasaplayer.core.common.ui.icons.RepeatIcon
import com.example.nyasaplayer.core.common.ui.icons.RepeatOneIcon
import com.example.nyasaplayer.core.common.ui.icons.ShuffleIcon
import com.example.nyasaplayer.core.common.ui.icons.SkipNextIcon
import com.example.nyasaplayer.core.common.ui.icons.SkipPreviousIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextTertiary
import com.example.nyasaplayer.core.common.util.formatDuration
import com.example.nyasaplayer.core.playback.PlaybackSnapshot
import com.example.nyasaplayer.core.playback.RepeatMode

private val FullPlayerAlbumArtSize = 400.dp
private val PlayButtonSize = 112.dp
private val SkipButtonSize = 80.dp

@Suppress("LongParameterList")
@Composable
fun CarFullPlayerScreen(
    playback: PlaybackSnapshot,
    onCollapseClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    onLikeClick: () -> Unit = {},
) {
    val song = playback.currentSong

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground),
    ) {
        // Background gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(NyasaPrimaryDark.copy(alpha = 0.15f), Color.Transparent),
                        radius = 800f,
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            // Left — Album Art
            AsyncImage(
                model = song?.resolvedCoverUrl,
                contentDescription = song?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(FullPlayerAlbumArtSize)
                    .clip(RoundedCornerShape(24.dp)),
            )

            // Right — Controls
            PlayerControlsPanel(
                playback = playback,
                onCollapseClick = onCollapseClick,
                onPlayPauseClick = onPlayPauseClick,
                onSkipNextClick = onSkipNextClick,
                onSkipPreviousClick = onSkipPreviousClick,
                onShuffleClick = onShuffleClick,
                onRepeatClick = onRepeatClick,
                onSeek = onSeek,
                isLiked = isLiked,
                onLikeClick = onLikeClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun PlayerControlsPanel(
    playback: PlaybackSnapshot,
    onCollapseClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSeek: (Long) -> Unit,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = playback.currentSong

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        PlayerTopBar(
            albumName = song?.albumName?.ifBlank { "Now Playing" } ?: "Now Playing",
            onCollapseClick = onCollapseClick,
        )
        Spacer(modifier = Modifier.height(32.dp))
        TrackInfo(
            title = song?.title ?: "",
            artistAlbum = buildArtistAlbumText(song?.resolvedArtistName, song?.albumName),
        )
        Spacer(modifier = Modifier.height(32.dp))
        ProgressSlider(playback = playback, onSeek = onSeek)
        Spacer(modifier = Modifier.height(24.dp))
        MainControls(
            playback = playback,
            onPlayPauseClick = onPlayPauseClick,
            onSkipNextClick = onSkipNextClick,
            onSkipPreviousClick = onSkipPreviousClick,
            onShuffleClick = onShuffleClick,
            onRepeatClick = onRepeatClick,
        )
        Spacer(modifier = Modifier.height(16.dp))
        LikeButton(isLiked = isLiked, onClick = onLikeClick)
    }
}

@Composable
private fun PlayerTopBar(
    albumName: String,
    onCollapseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CircleIconButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = "Collapse",
            size = CarTouchTargetSize,
            onClick = onCollapseClick,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PLAYING FROM PLAYLIST", color = NyasaTextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(albumName, color = Color.White, fontSize = 18.sp)
        }
        CircleIconButton(
            icon = MoreHorizIcon,
            contentDescription = "More",
            size = CarTouchTargetSize,
            onClick = { /* More options */ },
        )
    }
}

@Composable
private fun TrackInfo(
    title: String,
    artistAlbum: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artistAlbum,
            color = NyasaTextSecondary,
            fontSize = 24.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgressSlider(
    playback: PlaybackSnapshot,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (playback.durationMs > 0) {
        (playback.currentPositionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(modifier = modifier) {
        Slider(
            value = progress,
            onValueChange = { fraction -> onSeek((fraction * playback.durationMs).toLong()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = NyasaPrimary,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatDuration(playback.currentPositionMs), color = NyasaTextSecondary, fontSize = 18.sp)
            Text(formatDuration(playback.durationMs), color = NyasaTextSecondary, fontSize = 18.sp)
        }
    }
}

@Composable
private fun MainControls(
    playback: PlaybackSnapshot,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = ShuffleIcon,
            contentDescription = "Shuffle",
            size = CarTouchTargetSize,
            tint = if (playback.isShuffled) NyasaPrimary else NyasaTextSecondary,
            onClick = onShuffleClick,
        )
        Spacer(modifier = Modifier.width(16.dp))
        CircleIconButton(
            icon = SkipPreviousIcon,
            contentDescription = "Previous",
            size = SkipButtonSize,
            iconSize = 36.dp,
            onClick = onSkipPreviousClick,
        )
        Spacer(modifier = Modifier.width(16.dp))
        PlayPauseButton(isPlaying = playback.isPlaying, onClick = onPlayPauseClick)
        Spacer(modifier = Modifier.width(16.dp))
        CircleIconButton(
            icon = SkipNextIcon,
            contentDescription = "Next",
            size = SkipButtonSize,
            iconSize = 36.dp,
            onClick = onSkipNextClick,
        )
        Spacer(modifier = Modifier.width(16.dp))

        val repeatIcon = if (playback.repeatMode == RepeatMode.One) RepeatOneIcon else RepeatIcon
        val repeatTint = if (playback.repeatMode != RepeatMode.Off) NyasaPrimary else NyasaTextSecondary
        CircleIconButton(
            icon = repeatIcon,
            contentDescription = "Repeat",
            size = CarTouchTargetSize,
            tint = repeatTint,
            onClick = onRepeatClick,
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(PlayButtonSize)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark))),
    ) {
        Icon(
            imageVector = if (isPlaying) PauseIcon else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun LikeButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircleIconButton(
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isLiked) "Unlike" else "Like",
            size = CarTouchTargetSize,
            iconSize = 24.dp,
            tint = if (isLiked) NyasaPrimary else NyasaTextSecondary,
            onClick = onClick,
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
    tint: Color = Color.White,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

private fun buildArtistAlbumText(artist: String?, album: String?): String = buildString {
    append(artist ?: "")
    if (!album.isNullOrBlank()) {
        append(" \u2022 $album")
    }
}
