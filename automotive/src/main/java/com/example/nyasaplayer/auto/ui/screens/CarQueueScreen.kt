package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.nyasaplayer.auto.ui.theme.CarListArtSize
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.NowPlayingOverlay
import com.example.nyasaplayer.core.common.ui.icons.MoreVertIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.util.formatDuration

private const val DisabledOpacity = 0.4f
private const val HelperChipFillOpacity = 0.12f
private const val HelperChipBorderOpacity = 0.3f
private const val CurrentTrackBorderWidth = 2
private const val DestructiveFillOpacity = 0.15f
private val QueueRowHeight = 96.dp
private val QueueRowCornerRadius = 16.dp
private val ClearButtonCornerRadius = 16.dp
private val HelperChipHeight = 56.dp
private val HelperChipCornerRadius = 24.dp
private val DestructiveRed = Color(0xFFEF5350)

@Suppress("LongParameterList")
@Composable
fun CarQueueScreen(
    queue: List<Song>,
    currentIndex: Int,
    isDriving: Boolean,
    onCloseClick: () -> Unit,
    onSkipTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
) {
    val upcomingCount = (queue.size - (currentIndex + 1)).coerceAtLeast(0)
    val upcomingDurationMs = remember(queue, currentIndex) {
        queue.drop((currentIndex + 1).coerceAtLeast(0)).sumOf { it.durationMs }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground),
    ) {
        QueueHeader(
            upcomingCount = upcomingCount,
            upcomingDurationMs = upcomingDurationMs,
            isDriving = isDriving,
            canClear = !isDriving && queue.size > 1,
            onCloseClick = onCloseClick,
            onClearClick = onClearQueue,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )

        if (isDriving) {
            DrivingHelperChip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(
                items = queue,
                key = { index, song -> "$index:${song.mediaId}" },
            ) { index, song ->
                QueueRow(
                    song = song,
                    isCurrentTrack = index == currentIndex,
                    isPlaying = isPlaying,
                    isDriving = isDriving,
                    onClick = { onSkipTo(index) },
                    onRemove = { onRemove(index) },
                )
            }
        }
    }
}

@Composable
private fun QueueHeader(
    upcomingCount: Int,
    upcomingDurationMs: Long,
    isDriving: Boolean,
    canClear: Boolean,
    onCloseClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Up Next",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = formatUpcomingLine(upcomingCount, upcomingDurationMs),
                color = NyasaTextSecondary,
                fontSize = 18.sp,
            )
        }
        ClearQueuePill(
            enabled = canClear,
            onClick = onClearClick,
            isDriving = isDriving,
        )
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(
            onClick = onCloseClick,
            modifier = Modifier
                .size(CarTouchTargetSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close queue",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ClearQueuePill(
    enabled: Boolean,
    onClick: () -> Unit,
    isDriving: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else DisabledOpacity
    Box(
        modifier = modifier
            .height(CarTouchTargetSize)
            .clip(RoundedCornerShape(ClearButtonCornerRadius))
            .background(DestructiveRed.copy(alpha = DestructiveFillOpacity * alpha))
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = DestructiveRed.copy(alpha = alpha),
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = if (isDriving) "Locked" else "Clear Queue",
                color = DestructiveRed.copy(alpha = alpha),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DrivingHelperChip(modifier: Modifier = Modifier) {
    val chipShape = RoundedCornerShape(HelperChipCornerRadius)
    Row(
        modifier = modifier
            .height(HelperChipHeight)
            .clip(chipShape)
            .background(NyasaPrimary.copy(alpha = HelperChipFillOpacity))
            .border(1.dp, NyasaPrimary.copy(alpha = HelperChipBorderOpacity), chipShape)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = NyasaPrimary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Park the car to reorder or clear your queue.",
            color = NyasaPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun QueueRow(
    song: Song,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isDriving: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(QueueRowHeight)
            .clip(RoundedCornerShape(QueueRowCornerRadius))
            .then(
                if (isCurrentTrack) {
                    Modifier.border(
                        CurrentTrackBorderWidth.dp,
                        Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark)),
                        RoundedCornerShape(QueueRowCornerRadius),
                    )
                } else {
                    Modifier
                },
            )
            .background(NyasaSurface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NowPlayingOverlay(
            isCurrentTrack = isCurrentTrack,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(12.dp),
        ) {
            AsyncImage(
                model = song.resolvedCoverUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(CarListArtSize)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrentTrack) NyasaPrimary else Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.resolvedArtistName,
                color = NyasaTextSecondary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatDuration(song.durationMs),
            color = NyasaTextSecondary,
            fontSize = 16.sp,
        )
        OverflowButton(
            enabled = !isDriving && !isCurrentTrack,
            onClick = { showRemoveConfirm = true },
        )
    }

    if (showRemoveConfirm) {
        RemoveConfirmDialog(
            songTitle = song.title,
            onConfirm = {
                onRemove()
                showRemoveConfirm = false
            },
            onDismiss = { showRemoveConfirm = false },
        )
    }
}

@Composable
private fun OverflowButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else DisabledOpacity
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(CarTouchTargetSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f * alpha)),
    ) {
        Icon(
            imageVector = MoreVertIcon,
            contentDescription = "Remove from queue",
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun RemoveConfirmDialog(
    songTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(NyasaSurface2)
                .clickable(enabled = false, onClick = {})
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Remove from queue?",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "\"$songTitle\" will no longer play.",
                color = NyasaTextSecondary,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DialogButton(label = "Cancel", destructive = false, onClick = onDismiss)
                DialogButton(label = "Remove", destructive = true, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(CarTouchTargetSize)
            .width(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (destructive) DestructiveRed else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatUpcomingLine(count: Int, durationMs: Long): String {
    if (count <= 0) return "End of queue"
    val noun = if (count == 1) "song" else "songs"
    return "$count $noun · ${formatDurationLong(durationMs)} remaining"
}

private fun formatDurationLong(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}hr ${minutes}min" else "${minutes}min"
}
