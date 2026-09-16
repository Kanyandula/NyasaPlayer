package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.components.CarDownloadRow
import com.example.nyasaplayer.auto.ui.components.CarDrivingHelperChip
import com.example.nyasaplayer.auto.ui.components.CarEmptyState
import com.example.nyasaplayer.auto.ui.components.CarModalCard
import com.example.nyasaplayer.auto.ui.components.CarModalScrim
import com.example.nyasaplayer.auto.ui.components.CarPillButton
import com.example.nyasaplayer.auto.ui.theme.CarSignOutRed
import com.example.nyasaplayer.auto.ui.theme.CarSignOutRedSolid
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground

private const val DisabledOpacity = 0.4f
private const val DestructiveFillOpacity = 0.15f
private val ListPadding = 24.dp
private val RowSpacing = 12.dp
private val PillCornerRadius = 16.dp
private val ConfirmButtonWidth = 200.dp

/**
 * Downloads — screen 15.
 *
 * Reached from Library, and the one content screen that is worth anything with the car off the
 * network: a tap plays from the file, not the stream (`DownloadRepository.resolveLocalUri`).
 *
 * **Viewable while driving, mutable only while parked.** Remove, Remove all and Retry stay on
 * screen and go disabled in motion, with a banner saying why, rather than vanishing — the driver
 * sees the same screen they parked with, and no control silently does nothing (FR-2.6). The list
 * truncates to the platform's item cap in motion like every other content list (FR-2.4).
 *
 * The contract's "storage bar" ships as a storage *line*. A bar needs a capacity to fill, and the
 * app knows how much it has stored, not how much the head unit has free; drawing a bar against an
 * invented total would tell the driver something untrue.
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
fun CarDownloadsScreen(
    items: List<CarDownloadItem>,
    isDriving: Boolean,
    maxItems: Int,
    onBackClick: () -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    onRemove: (String) -> Unit,
    onRemoveAll: () -> Unit,
    onRetry: (String) -> Unit,
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
) {
    // Keyed on isDriving: a confirmation opened at the kerb must not survive into motion, where
    // its Remove All button would delete every downloaded file while the car is moving (FR-2.5).
    var showRemoveAllConfirm by remember(isDriving) { mutableStateOf(false) }
    val displayItems = remember(items, maxItems, isDriving) {
        downloadDisplayItems(items, maxItems, isDriving)
    }
    // Tapping a row plays the downloaded songs as a queue, so a finished download is followed by
    // the next one rather than by a track with no file behind it.
    val playable = remember(displayItems) { displayItems.filter { it.isPlayable }.map { it.song } }

    Column(modifier = modifier.fillMaxSize()) {
        DownloadsHeader(
            summary = formatDownloadSummary(items),
            canRemoveAll = canRemoveAllDownloads(items, isDriving),
            isDriving = isDriving,
            onRemoveAllClick = { showRemoveAllConfirm = true },
            onBackClick = onBackClick,
            modifier = Modifier.padding(horizontal = ListPadding, vertical = 16.dp),
        )

        if (isDriving) {
            CarDrivingHelperChip(
                message = "Park the car to remove or retry downloads. Downloaded songs still play.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ListPadding, vertical = 8.dp),
            )
        }

        if (displayItems.isEmpty()) {
            CarEmptyState(
                title = "No downloads yet",
                body = "Download songs while parked and they play without a connection.",
                actionLabel = "Browse Music",
                onAction = onBrowseClick,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = ListPadding),
            contentPadding = PaddingValues(bottom = ListPadding),
            verticalArrangement = Arrangement.spacedBy(RowSpacing),
        ) {
            items(items = displayItems, key = { it.song.mediaId }) { item ->
                CarDownloadRow(
                    item = item,
                    isDriving = isDriving,
                    onClick = { onSongClick(playable, item.song) },
                    onRemove = { onRemove(item.song.mediaId) },
                    onRetry = { onRetry(item.song.mediaId) },
                    isCurrentTrack = item.song.mediaId == currentlyPlayingMediaId,
                    isPlaying = isPlaying,
                )
            }
        }
    }

    if (showRemoveAllConfirm) {
        RemoveAllConfirmation(
            onConfirm = {
                onRemoveAll()
                showRemoveAllConfirm = false
            },
            onDismiss = { showRemoveAllConfirm = false },
        )
    }
}

@Composable
private fun DownloadsHeader(
    summary: String,
    canRemoveAll: Boolean,
    isDriving: Boolean,
    onRemoveAllClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Downloads",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = summary,
                color = CarTextSecondary,
                fontSize = 18.sp,
            )
        }
        RemoveAllPill(
            enabled = canRemoveAll,
            isDriving = isDriving,
            onClick = onRemoveAllClick,
        )
        Spacer(modifier = Modifier.width(16.dp))
        CarPillButton(label = "Back", onClick = onBackClick, filled = false)
    }
}

/**
 * Destructive wash rather than a solid fill, matching the queue's Clear.
 *
 * The red-on-15%-wash pair is the AA exception the design records, so this button adds no new one
 * (PRD §12 criterion 3) — but only if the wash lands on one of the surfaces that exception names.
 * Downloads is drawn over the ambient gradient, so the wash is laid on an opaque base rather than
 * straight onto the backdrop; without one the pair measured 4.66:1 against a colour the measurement
 * did not recognise. The base is [NyasaBackground], the same one the queue's Clear composites over:
 * the recorded exception still has to clear AA, and over the lighter [CarGlass] this pair falls to
 * 4.27:1 and stops being the recorded pair at all.
 */
@Composable
private fun RemoveAllPill(
    enabled: Boolean,
    isDriving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else DisabledOpacity
    Box(
        modifier = modifier
            .height(CarTouchTargetSize)
            .clip(RoundedCornerShape(PillCornerRadius))
            .background(NyasaBackground)
            .background(CarSignOutRed.copy(alpha = DestructiveFillOpacity * alpha))
            .clickable(enabled = enabled, onClick = onClick)
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
                tint = CarSignOutRed.copy(alpha = alpha),
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = if (isDriving) "Locked" else "Remove All",
                color = CarSignOutRed.copy(alpha = alpha),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun RemoveAllConfirmation(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CarModalScrim(onDismiss = onDismiss) {
        CarModalCard {
            Text(
                text = "Remove all downloads?",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Every downloaded song is deleted from this car. " +
                    "You will need a connection to play them again.",
                color = CarTextSecondary,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ConfirmButton(label = "Cancel", destructive = false, onClick = onDismiss)
                ConfirmButton(label = "Remove", destructive = true, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun ConfirmButton(
    label: String,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(CarTouchTargetSize)
            .width(ConfirmButtonWidth)
            .clip(RoundedCornerShape(PillCornerRadius))
            .background(if (destructive) CarSignOutRedSolid else Color.White.copy(alpha = 0.1f))
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
