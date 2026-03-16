package com.example.nyasaplayer.screens.home

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.nyasaplayer.core.common.ui.components.NowPlayingOverlay
import com.example.nyasaplayer.core.common.ui.components.NyasaErrorScreen
import com.example.nyasaplayer.core.common.ui.icons.ChevronRightIcon
import com.example.nyasaplayer.core.common.ui.icons.NotificationIcon
import com.example.nyasaplayer.core.common.ui.icons.ProfileIcon
import com.example.nyasaplayer.core.common.ui.theme.AppTheme
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface3
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.util.greetingResource
import com.example.nyasaplayer.ui.preview.PreviewHomeUiState

@Composable
fun HomeScreen(
    onSongClick: (List<Song>, Song) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isCurrentlyPlaying: Boolean = false,
    viewModel: HomeViewModel = hiltViewModel(),
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
        uiState.isLoading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }
        else -> HomeContent(
            uiState = uiState,
            onSongClick = onSongClick,
            onProfileClick = onProfileClick,
            currentlyPlayingMediaId = currentlyPlayingMediaId,
            isCurrentlyPlaying = isCurrentlyPlaying,
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onSongClick: (List<Song>, Song) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isCurrentlyPlaying: Boolean = false,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        item {
            HomeHeader(
                photoUrl = uiState.photoUrl,
                onProfileClick = onProfileClick,
                modifier = Modifier.statusBarsPadding(),
            )
        }
        recentlyPlayedSection(uiState.recentlyPlayed, onSongClick, currentlyPlayingMediaId, isCurrentlyPlaying)
        feedSections(uiState, onSongClick, currentlyPlayingMediaId, isCurrentlyPlaying)
    }
}

private fun LazyListScope.recentlyPlayedSection(
    recentlyPlayed: List<Song>,
    onSongClick: (List<Song>, Song) -> Unit,
    currentlyPlayingMediaId: String?,
    isCurrentlyPlaying: Boolean,
) {
    if (recentlyPlayed.isEmpty()) return
    item(key = "recently_played_header") {
        SectionHeader(title = stringResource(R.string.recently_played))
    }
    item(key = "recently_played") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(recentlyPlayed, key = { "recent_${it.mediaId}" }) { song ->
                val isCurrentTrack = song.mediaId == currentlyPlayingMediaId
                SongCard(
                    song = song,
                    onClick = { onSongClick(recentlyPlayed, song) },
                    isCurrentTrack = isCurrentTrack,
                    isPlaying = isCurrentTrack && isCurrentlyPlaying,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun LazyListScope.feedSections(
    uiState: HomeUiState,
    onSongClick: (List<Song>, Song) -> Unit,
    currentlyPlayingMediaId: String?,
    isCurrentlyPlaying: Boolean,
) {
    uiState.sections.forEach { section ->
        when (section.type) {
            "horizontal_scroll" -> {
                item(key = "${section.id}_header") {
                    SectionHeader(title = section.title)
                }
                item(key = section.id) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(section.songs, key = { it.mediaId }) { song ->
                            val isCurrentTrack = song.mediaId == currentlyPlayingMediaId
                            SongCard(
                                song = song,
                                onClick = { onSongClick(section.songs, song) },
                                isCurrentTrack = isCurrentTrack,
                                isPlaying = isCurrentTrack && isCurrentlyPlaying,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            "grid" -> {
                item(key = "${section.id}_header") {
                    SectionHeader(title = section.title)
                }
                item(key = section.id) {
                    QuickPicksGrid(
                        songs = section.songs,
                        onSongClick = { song -> onSongClick(section.songs, song) },
                        currentlyPlayingMediaId = currentlyPlayingMediaId,
                        isCurrentlyPlaying = isCurrentlyPlaying,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            else -> {
                item(key = "${section.id}_header") {
                    SectionHeader(title = section.title)
                }
                items(section.songs, key = { it.mediaId }) { song ->
                    val isCurrentTrack = song.mediaId == currentlyPlayingMediaId
                    SongRow(
                        song = song,
                        onClick = { onSongClick(uiState.allSongs, song) },
                        isCurrentTrack = isCurrentTrack,
                        isPlaying = isCurrentTrack && isCurrentlyPlaying,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    photoUrl: String,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greetingResource(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.greeting_subtitle),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
        }
        IconButton(onClick = { /* notifications */ }) {
            Icon(
                imageVector = NotificationIcon,
                contentDescription = stringResource(R.string.notifications),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = stringResource(R.string.profile),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onProfileClick),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NyasaSurface3)
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ProfileIcon,
                    contentDescription = stringResource(R.string.profile),
                    tint = NyasaTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = ChevronRightIcon,
            contentDescription = stringResource(R.string.see_all),
            tint = NyasaTextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun QuickPicksGrid(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isCurrentlyPlaying: Boolean = false,
) {
    val gridSongs = songs.take(4)
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (rowIndex in 0..1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (colIndex in 0..1) {
                    val index = rowIndex * 2 + colIndex
                    if (index < gridSongs.size) {
                        val isCurrentTrack = gridSongs[index].mediaId == currentlyPlayingMediaId
                        QuickPickCard(
                            song = gridSongs[index],
                            onClick = { onSongClick(gridSongs[index]) },
                            isCurrentTrack = isCurrentTrack,
                            isPlaying = isCurrentTrack && isCurrentlyPlaying,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NyasaSurface2)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NowPlayingOverlay(
            isCurrentTrack = isCurrentTrack,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(6.dp),
        ) {
            AsyncImage(
                model = song.resolvedCoverUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.resolvedArtistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SongCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
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
                    .size(140.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.resolvedArtistName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(NyasaSurface2, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NowPlayingOverlay(
            isCurrentTrack = isCurrentTrack,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(8.dp),
        ) {
            AsyncImage(
                model = song.resolvedCoverUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
            )
            Text(
                text = song.resolvedArtistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AppTheme {
        HomeContent(
            uiState = PreviewHomeUiState,
            onSongClick = { _, _ -> },
            onProfileClick = {},
            currentlyPlayingMediaId = "1",
            isCurrentlyPlaying = true,
        )
    }
}
