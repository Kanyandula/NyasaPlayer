package com.example.nyasaplayer.screens.library

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.nyasaplayer.R
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.NyasaErrorScreen
import com.example.nyasaplayer.core.common.ui.icons.HeartIcon
import com.example.nyasaplayer.core.common.ui.icons.MoreHorizIcon
import com.example.nyasaplayer.core.common.ui.theme.AppTheme
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextTertiary
import com.example.nyasaplayer.core.common.util.formatDuration
import com.example.nyasaplayer.download.SongDownloadManager
import com.example.nyasaplayer.screens.common.ShufflePlayButton
import com.example.nyasaplayer.screens.common.SongOverflowWithDownload
import com.example.nyasaplayer.screens.common.TrackNumberIndicator
import com.example.nyasaplayer.ui.preview.PreviewSongsWithDuration

@Composable
fun LibraryScreen(
    onSongClick: (List<Song>, Song) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isCurrentlyPlaying: Boolean = false,
    downloadManager: SongDownloadManager? = null,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.errorMessage != null -> NyasaErrorScreen(
            title = if (uiState.isNetworkError) {
                stringResource(R.string.error_no_connection_title)
            } else {
                stringResource(R.string.error_generic_title)
            },
            subtitle = if (uiState.isNetworkError) {
                stringResource(R.string.error_no_connection_subtitle)
            } else {
                stringResource(R.string.error_generic_subtitle)
            },
            onRetry = viewModel::retry,
            isNetworkError = uiState.isNetworkError,
            buttonText = stringResource(R.string.try_again),
            modifier = modifier,
        )
        else -> LibraryContent(
            songs = uiState.songs,
            currentlyPlayingMediaId = currentlyPlayingMediaId,
            isCurrentlyPlaying = isCurrentlyPlaying,
            onSongClick = onSongClick,
            onShufflePlay = onShufflePlay,
            onToggleLike = viewModel::removeLikedSong,
            downloadManager = downloadManager,
            modifier = modifier,
        )
    }
}

@Composable
private fun LibraryContent(
    songs: List<Song>,
    currentlyPlayingMediaId: String?,
    isCurrentlyPlaying: Boolean,
    onSongClick: (List<Song>, Song) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onToggleLike: (String) -> Unit,
    downloadManager: SongDownloadManager?,
    modifier: Modifier = Modifier,
) {
    var selectedSong by remember { mutableStateOf<Song?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        LibraryHeader(songCount = songs.size)

        Spacer(modifier = Modifier.height(12.dp))

        FilterChips()

        Spacer(modifier = Modifier.height(12.dp))

        ShufflePlayButton(
            onClick = { onShufflePlay(songs) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(songs, key = { _, song -> song.mediaId }) { index, song ->
                val isCurrentTrack = song.mediaId == currentlyPlayingMediaId
                LibrarySongRow(
                    song = song,
                    trackNumber = index + 1,
                    isCurrentTrack = isCurrentTrack,
                    isAnimating = isCurrentTrack && isCurrentlyPlaying,
                    onClick = { onSongClick(songs, song) },
                    onMoreClick = { selectedSong = song },
                )
            }
        }
    }

    selectedSong?.let { song ->
        SongOverflowWithDownload(
            song = song,
            downloadManager = downloadManager,
            onDismiss = { selectedSong = null },
            isLiked = true,
            onToggleLike = {
                selectedSong = null
                onToggleLike(song.mediaId)
            },
        )
    }
}

@Composable
private fun LibraryHeader(
    songCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = HeartIcon,
            contentDescription = null,
            tint = NyasaPrimary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.liked_songs),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.song_count, songCount),
                style = MaterialTheme.typography.bodySmall,
                color = NyasaTextSecondary,
            )
        }
    }
}

@Composable
private fun FilterChips(modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                label = stringResource(R.string.sort),
                selected = false,
            )
        }
        item {
            FilterChip(
                label = stringResource(R.string.filter),
                selected = false,
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (selected) NyasaPrimary else NyasaSurface2
    val textColor = if (selected) Color.White else NyasaTextSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
        )
    }
}

private val SongRowThumbnailSize = 48.dp
private val SongRowThumbnailRadius = 8.dp
private val SongRowThumbnailSpacing = 12.dp

@Composable
private fun LibrarySongRow(
    song: Song,
    trackNumber: Int,
    isCurrentTrack: Boolean,
    isAnimating: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
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
        Spacer(modifier = Modifier.width(SongRowThumbnailSpacing))
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
        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = MoreHorizIcon,
                contentDescription = stringResource(R.string.more_options),
                tint = NyasaTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun LibraryScreenPreview() {
    AppTheme {
        LibraryContent(
            songs = PreviewSongsWithDuration,
            currentlyPlayingMediaId = "1",
            isCurrentlyPlaying = true,
            onSongClick = { _, _ -> },
            onShufflePlay = { },
            onToggleLike = { },
            downloadManager = null,
        )
    }
}
