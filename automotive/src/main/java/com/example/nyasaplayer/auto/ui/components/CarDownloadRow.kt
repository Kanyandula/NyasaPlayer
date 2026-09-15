package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.auto.ui.screens.CarDownloadItem
import com.example.nyasaplayer.auto.ui.screens.formatDownloadSize
import com.example.nyasaplayer.auto.ui.theme.CarErrorText
import com.example.nyasaplayer.auto.ui.theme.CarGlass
import com.example.nyasaplayer.auto.ui.theme.CarListArtSize
import com.example.nyasaplayer.auto.ui.theme.CarOutline
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.core.common.ui.components.NowPlayingOverlay
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus

private const val DisabledOpacity = 0.4f
private const val PercentFull = 100
private const val ControlWashOpacity = 0.1f
private val RowHeight = 96.dp
private val RowCornerRadius = 16.dp
private val ArtCornerRadius = 12.dp
private val ProgressTrackHeight = 6.dp

/**
 * A Downloads row — screen 15's list item.
 *
 * Four statuses, one row: a finished download plays on tap and offers Remove; one in flight shows
 * its progress and is not tappable, because there is no file behind it yet; a failed one offers
 * Retry; a queued one says so.
 *
 * **Every mutation here is parked-only.** While driving the controls stay visible and disabled
 * rather than disappearing, so the row does not change shape under the driver's eyes, and the
 * screen's banner says why — a silent no-op is prohibited (FR-2.6).
 */
@Suppress("LongParameterList")
@Composable
fun CarDownloadRow(
    item: CarDownloadItem,
    isDriving: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
) {
    val rowShape = RoundedCornerShape(RowCornerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clip(rowShape)
            .background(CarGlass)
            // Only a completed download has a file; the others are not destinations.
            .clickable(enabled = item.isPlayable, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NowPlayingOverlay(
            isCurrentTrack = isCurrentTrack,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ArtCornerRadius),
        ) {
            AsyncImage(
                model = item.song.resolvedCoverUrl,
                contentDescription = item.song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(CarListArtSize)
                    .clip(RoundedCornerShape(ArtCornerRadius)),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.song.title,
                color = if (isCurrentTrack) NyasaGold else Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusLine(item = item)
        }
        RowActions(
            item = item,
            isDriving = isDriving,
            onRemove = onRemove,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun StatusLine(item: CarDownloadItem, modifier: Modifier = Modifier) {
    when (item.status) {
        DownloadStatus.Completed -> Text(
            text = "${item.song.resolvedArtistName} · ${formatDownloadSize(item.sizeBytes)}",
            color = CarTextSecondary,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )

        DownloadStatus.Downloading -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Downloading · ${item.progress}%",
                color = CarTextSecondary,
                fontSize = 16.sp,
                maxLines = 1,
            )
            // Two boxes rather than LinearProgressIndicator: the fill is a static fraction, so
            // the component buys only an API surface that moved between Material3 releases.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ProgressTrackHeight)
                    .clip(RoundedCornerShape(ProgressTrackHeight))
                    .background(CarOutline),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(item.progress.coerceIn(0, PercentFull) / PercentFull.toFloat())
                        .background(NyasaGold),
                )
            }
        }

        DownloadStatus.Pending -> Text(
            text = "Waiting to download",
            color = CarTextSecondary,
            fontSize = 16.sp,
            maxLines = 1,
            modifier = modifier,
        )

        DownloadStatus.Failed -> Text(
            text = "Couldn't download",
            color = CarErrorText,
            fontSize = 16.sp,
            maxLines = 1,
            modifier = modifier,
        )
    }
}

@Composable
private fun RowActions(
    item: CarDownloadItem,
    isDriving: Boolean,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.status == DownloadStatus.Failed) {
            RowAction(
                icon = Icons.Filled.Refresh,
                description = "Retry download of ${item.song.title}",
                enabled = !isDriving,
                onClick = onRetry,
            )
        }
        // Absent while a download is running: there is no file to remove yet, and cancelling
        // mid-flight is not a screen-15 action (it stays on SongDownloadManager for the phone).
        if (item.status != DownloadStatus.Downloading) {
            RowAction(
                icon = Icons.Filled.Delete,
                description = "Remove download of ${item.song.title}",
                enabled = !isDriving,
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun RowAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else DisabledOpacity
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(CarTouchTargetSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = ControlWashOpacity * alpha)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier.size(24.dp),
        )
    }
}
