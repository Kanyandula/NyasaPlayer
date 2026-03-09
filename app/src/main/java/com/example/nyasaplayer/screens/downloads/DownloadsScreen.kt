package com.example.nyasaplayer.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.nyasaplayer.R
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.NyasaErrorScreen
import com.example.nyasaplayer.core.common.ui.components.ShufflePlayButton
import com.example.nyasaplayer.core.common.ui.icons.DownloadIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextTertiary
import com.example.nyasaplayer.core.common.util.formatDuration
import com.example.nyasaplayer.screens.common.TrackNumberIndicator

private const val BytesPerMb = 1_048_576.0
private const val MbPerGb = 1024.0

@Composable
fun DownloadsScreen(
    onSongClick: (List<Song>, Song) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isCurrentlyPlaying: Boolean = false,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.isLoading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = NyasaPrimary)
        }
        uiState.errorMessage != null -> NyasaErrorScreen(
            title = stringResource(R.string.error_generic_title),
            subtitle = stringResource(R.string.error_generic_subtitle),
            onRetry = viewModel::retry,
            buttonText = stringResource(R.string.try_again),
            modifier = modifier,
        )
        else -> DownloadsContent(
            songs = uiState.songs,
            downloadCount = uiState.downloadCount,
            totalSizeBytes = uiState.totalSizeBytes,
            currentlyPlayingMediaId = currentlyPlayingMediaId,
            isCurrentlyPlaying = isCurrentlyPlaying,
            onSongClick = onSongClick,
            onShufflePlay = onShufflePlay,
            onBack = onBack,
            onRemoveDownload = viewModel::removeDownload,
            onRemoveAll = viewModel::removeAllDownloads,
            modifier = modifier,
        )
    }
}

@Composable
private fun DownloadsContent(
    songs: List<Song>,
    downloadCount: Int,
    totalSizeBytes: Long,
    currentlyPlayingMediaId: String?,
    isCurrentlyPlaying: Boolean,
    onSongClick: (List<Song>, Song) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onBack: () -> Unit,
    onRemoveDownload: (String) -> Unit,
    onRemoveAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemoveAllDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        DownloadsHeader(
            downloadCount = downloadCount,
            totalSizeBytes = totalSizeBytes,
            onBack = onBack,
            onRemoveAll = { showRemoveAllDialog = true },
            hasDownloads = songs.isNotEmpty(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (songs.isNotEmpty()) {
            ShufflePlayButton(
                onClick = { onShufflePlay(songs) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (songs.isEmpty()) {
            EmptyDownloadsMessage(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(songs, key = { _, song -> song.mediaId }) { index, song ->
                    val isCurrentTrack = song.mediaId == currentlyPlayingMediaId
                    DownloadSongRow(
                        song = song,
                        trackNumber = index + 1,
                        isCurrentTrack = isCurrentTrack,
                        isAnimating = isCurrentTrack && isCurrentlyPlaying,
                        onClick = { onSongClick(songs, song) },
                        onRemove = { onRemoveDownload(song.mediaId) },
                    )
                }
            }
        }
    }

    if (showRemoveAllDialog) {
        RemoveAllConfirmationDialog(
            onConfirm = {
                showRemoveAllDialog = false
                onRemoveAll()
            },
            onDismiss = { showRemoveAllDialog = false },
        )
    }
}

@Composable
private fun RemoveAllConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.remove_all_downloads_title)) },
        text = { Text(text = stringResource(R.string.remove_all_downloads_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.remove_all_downloads),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun DownloadsHeader(
    downloadCount: Int,
    totalSizeBytes: Long,
    onBack: () -> Unit,
    onRemoveAll: () -> Unit,
    hasDownloads: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.navigate_back),
                tint = Color.White,
            )
        }
        Icon(
            imageVector = DownloadIcon,
            contentDescription = null,
            tint = NyasaPrimary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.downloads),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
            Text(
                text = formatStorageSummary(downloadCount, totalSizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = NyasaTextSecondary,
            )
        }
        if (hasDownloads) {
            IconButton(onClick = onRemoveAll) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_all_downloads),
                    tint = NyasaTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun EmptyDownloadsMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = DownloadIcon,
                contentDescription = null,
                tint = NyasaTextTertiary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_downloads_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.no_downloads_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = NyasaTextSecondary,
            )
        }
    }
}

private val SongRowThumbnailSize = 48.dp
private val SongRowThumbnailRadius = 8.dp

@Composable
private fun DownloadSongRow(
    song: Song,
    trackNumber: Int,
    isCurrentTrack: Boolean,
    isAnimating: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowBackground = if (isCurrentTrack) NyasaPrimary.copy(alpha = 0.15f) else Color.Transparent
    val titleColor = if (isCurrentTrack) NyasaPrimary else Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackNumberIndicator(
            trackNumber = trackNumber,
            isCurrentTrack = isCurrentTrack,
            isAnimating = isAnimating,
        )
        Spacer(modifier = Modifier.width(8.dp))
        AsyncImage(
            model = song.resolvedCoverUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(SongRowThumbnailSize)
                .clip(RoundedCornerShape(SongRowThumbnailRadius)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.resolvedArtistName,
                style = MaterialTheme.typography.bodySmall,
                color = NyasaTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (song.durationMs > 0) {
            Text(
                text = formatDuration(song.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = NyasaTextTertiary,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.remove_download),
                tint = NyasaTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatStorageSummary(count: Int, sizeBytes: Long): String {
    val sizeMb = sizeBytes / BytesPerMb
    return when {
        sizeMb >= MbPerGb -> "$count songs \u00B7 ${"%.1f".format(sizeMb / MbPerGb)} GB"
        sizeMb >= 1 -> "$count songs \u00B7 ${"%.1f".format(sizeMb)} MB"
        else -> "$count songs"
    }
}
