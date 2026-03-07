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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
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
import com.example.nyasaplayer.core.common.ui.icons.ShuffleIcon
import com.example.nyasaplayer.core.common.ui.theme.AppTheme
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextTertiary
import com.example.nyasaplayer.core.common.util.formatDuration
import com.example.nyasaplayer.download.SongDownloadManager
import com.example.nyasaplayer.screens.common.SongOverflowWithDownload
import com.example.nyasaplayer.ui.preview.PreviewSongsWithDuration

@Composable
fun LibraryScreen(
    onSongClick: (List<Song>, Song) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
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
            onSongClick = onSongClick,
            onShufflePlay = onShufflePlay,
            downloadManager = downloadManager,
            modifier = modifier,
        )
    }
}

@Composable
private fun LibraryContent(
    songs: List<Song>,
    currentlyPlayingMediaId: String?,
    onSongClick: (List<Song>, Song) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
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
                LibrarySongRow(
                    song = song,
                    trackNumber = index + 1,
                    isPlaying = song.mediaId == currentlyPlayingMediaId,
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

@Composable
private fun ShufflePlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(NyasaPrimaryDark, NyasaPrimary)),
                    RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = ShuffleIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.shuffle_play),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

private val SongRowThumbnailSize = 48.dp
private val SongRowThumbnailRadius = 8.dp
private val SongRowThumbnailSpacing = 12.dp

@Composable
private fun TrackNumberIndicator(trackNumber: Int, isPlaying: Boolean) {
    Box(
        modifier = Modifier.width(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = NyasaPrimary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = trackNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = NyasaTextTertiary,
            )
        }
    }
}

@Composable
private fun LibrarySongRow(
    song: Song,
    trackNumber: Int,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowBackground = if (isPlaying) NyasaPrimary.copy(alpha = 0.15f) else Color.Transparent
    val titleColor = if (isPlaying) NyasaPrimary else Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackNumberIndicator(trackNumber = trackNumber, isPlaying = isPlaying)
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
            onSongClick = { _, _ -> },
            onShufflePlay = { },
            downloadManager = null,
        )
    }
}
