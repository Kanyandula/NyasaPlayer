package com.example.nyasaplayer.core.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.core.common.R
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.icons.AlbumIcon
import com.example.nyasaplayer.core.common.ui.icons.DownloadIcon
import com.example.nyasaplayer.core.common.ui.icons.PlayNextIcon
import com.example.nyasaplayer.core.common.ui.icons.PlaylistAddIcon
import com.example.nyasaplayer.core.common.ui.icons.PlaylistRemoveIcon
import com.example.nyasaplayer.core.common.ui.icons.ProfileIcon
import com.example.nyasaplayer.core.common.ui.icons.QueueMusicIcon
import com.example.nyasaplayer.core.common.ui.icons.RadioIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface3
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface4
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextTertiary
import com.example.nyasaplayer.core.common.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOverflowSheet(
    song: Song,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    onToggleLike: () -> Unit = {},
    downloadState: SongDownloadState = SongDownloadState.NotDownloaded,
    onDownloadClick: (Song) -> Unit = {},
    onRemoveDownloadClick: (Song) -> Unit = {},
    onSaveToPlaylist: () -> Unit = {},
    showRemoveFromPlaylist: Boolean = false,
    onRemoveFromPlaylist: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NyasaSurface2,
        dragHandle = { BottomSheetDefaults.DragHandle(color = NyasaTextTertiary) },
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            SheetHeader(song = song, isLiked = isLiked, onToggleLike = onToggleLike, onDismiss = onDismiss)
            Spacer(modifier = Modifier.height(16.dp))
            QuickActionsRow()
            HorizontalDivider(
                color = NyasaSurface4,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            SheetMenuItems(
                song = song,
                isLiked = isLiked,
                onToggleLike = onToggleLike,
                downloadState = downloadState,
                onDownloadClick = onDownloadClick,
                onRemoveDownloadClick = onRemoveDownloadClick,
                onSaveToPlaylist = onSaveToPlaylist,
                showRemoveFromPlaylist = showRemoveFromPlaylist,
                onRemoveFromPlaylist = onRemoveFromPlaylist,
            )
        }
    }
}

@Composable
private fun SheetHeader(
    song: Song,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                append(song.resolvedArtistName)
                if (song.durationMs > 0) {
                    append(" \u2022 ")
                    append(formatDuration(song.durationMs))
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NyasaTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleLike) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = stringResource(R.string.favorite),
                tint = if (isLiked) NyasaPrimary else NyasaTextSecondary,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
                tint = NyasaTextSecondary,
            )
        }
    }
}

@Composable
private fun QuickActionsRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickActionButton(
            icon = PlayNextIcon,
            label = stringResource(R.string.play_next),
            modifier = Modifier.weight(1f),
        )
        QuickActionButton(
            icon = RadioIcon,
            label = stringResource(R.string.start_mix),
            modifier = Modifier.weight(1f),
        )
        QuickActionButton(
            icon = Icons.Default.Share,
            label = stringResource(R.string.share),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NyasaSurface3)
            .clickable { }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun SheetMenuItems(
    song: Song,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    downloadState: SongDownloadState,
    onDownloadClick: (Song) -> Unit,
    onRemoveDownloadClick: (Song) -> Unit,
    onSaveToPlaylist: () -> Unit,
    showRemoveFromPlaylist: Boolean,
    onRemoveFromPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SheetMenuItem(icon = QueueMusicIcon, label = stringResource(R.string.add_to_queue))
        SheetMenuItem(
            icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = if (isLiked) {
                stringResource(R.string.remove_from_library)
            } else {
                stringResource(R.string.save_to_library)
            },
            onClick = onToggleLike,
        )
        DownloadMenuItem(
            song = song,
            downloadState = downloadState,
            onDownloadClick = onDownloadClick,
            onRemoveDownloadClick = onRemoveDownloadClick,
        )
        SheetMenuItem(
            icon = PlaylistAddIcon,
            label = stringResource(R.string.save_to_playlist),
            onClick = onSaveToPlaylist,
        )
        if (showRemoveFromPlaylist) {
            SheetMenuItem(
                icon = PlaylistRemoveIcon,
                label = stringResource(R.string.remove_from_playlist),
                onClick = onRemoveFromPlaylist,
            )
        }
        SheetMenuItem(icon = AlbumIcon, label = stringResource(R.string.go_to_album))
        SheetMenuItem(icon = ProfileIcon, label = stringResource(R.string.go_to_artist))
    }
}

@Composable
private fun DownloadMenuItem(
    song: Song,
    downloadState: SongDownloadState,
    onDownloadClick: (Song) -> Unit,
    onRemoveDownloadClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (downloadState) {
        SongDownloadState.NotDownloaded -> SheetMenuItem(
            icon = DownloadIcon,
            label = stringResource(R.string.download),
            onClick = { onDownloadClick(song) },
            modifier = modifier,
        )
        SongDownloadState.Downloading -> Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = NyasaPrimary,
                strokeWidth = 2.dp,
                strokeCap = StrokeCap.Round,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.download),
                style = MaterialTheme.typography.bodyLarge,
                color = NyasaTextSecondary,
            )
        }
        SongDownloadState.Downloaded -> SheetMenuItem(
            icon = Icons.Default.Delete,
            label = stringResource(R.string.remove_download),
            onClick = { onRemoveDownloadClick(song) },
            modifier = modifier,
        )
    }
}

@Composable
private fun SheetMenuItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NyasaTextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}
