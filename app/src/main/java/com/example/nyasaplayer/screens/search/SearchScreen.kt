package com.example.nyasaplayer.screens.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.nyasaplayer.R
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.NyasaErrorScreen
import com.example.nyasaplayer.core.common.ui.icons.MoreHorizIcon
import com.example.nyasaplayer.core.common.ui.icons.SearchIcon
import com.example.nyasaplayer.core.common.ui.theme.AppTheme
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface3
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextTertiary
import com.example.nyasaplayer.core.common.util.formatDuration
import com.example.nyasaplayer.download.SongDownloadManager
import com.example.nyasaplayer.screens.common.SongOverflowWithDownload
import com.example.nyasaplayer.ui.preview.PreviewGenres
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun SearchScreen(
    onSongClick: (List<Song>, Song) -> Unit,
    modifier: Modifier = Modifier,
    downloadManager: SongDownloadManager? = null,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.errorMessage != null && uiState.allSongs.isEmpty()) {
        NyasaErrorScreen(
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
        return
    }

    if (uiState.selectedGenre != null && uiState.query.isBlank()) {
        BackHandler { viewModel.onGenreBack() }
    }

    SearchContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onSongClick = onSongClick,
        onGenreClick = viewModel::onGenreSelected,
        onGenreBack = viewModel::onGenreBack,
        onToggleLike = viewModel::toggleLikeSong,
        isLikedProvider = viewModel::isLiked,
        downloadManager = downloadManager,
        modifier = modifier,
    )
}

@Composable
private fun SearchContent(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    onGenreClick: (Genre) -> Unit,
    onGenreBack: () -> Unit,
    onToggleLike: (String) -> Unit,
    isLikedProvider: (String) -> Flow<Boolean>,
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
        Text(
            text = stringResource(R.string.search),
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        )

        SearchBar(
            query = uiState.query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.query.isNotBlank()) {
            SearchResults(
                results = uiState.searchResults,
                onSongClick = { song -> onSongClick(uiState.searchResults, song) },
                onMoreClick = { song -> selectedSong = song },
                modifier = Modifier.weight(1f),
            )
        } else if (uiState.selectedGenre != null) {
            GenreSongsSection(
                genre = uiState.selectedGenre!!,
                songs = uiState.genreSongs,
                onSongClick = { song -> onSongClick(uiState.genreSongs, song) },
                onMoreClick = { song -> selectedSong = song },
                onBack = onGenreBack,
                modifier = Modifier.weight(1f),
            )
        } else {
            BrowseSection(
                genres = uiState.genres,
                onGenreClick = onGenreClick,
            )
        }
    }

    selectedSong?.let { song ->
        val isLiked by remember(song.mediaId) { isLikedProvider(song.mediaId) }
            .collectAsState(initial = false)
        SongOverflowWithDownload(
            song = song,
            downloadManager = downloadManager,
            onDismiss = { selectedSong = null },
            isLiked = isLiked,
            onToggleLike = { onToggleLike(song.mediaId) },
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NyasaSurface2)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SearchIcon,
            contentDescription = null,
            tint = NyasaTextTertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NyasaTextTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                singleLine = true,
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BrowseSection(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.browse_all),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(genres, key = { it.id }) { genre ->
                GenreCard(genre = genre, onClick = { onGenreClick(genre) })
            }
        }
    }
}

@Composable
private fun GenreCard(
    genre: Genre,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = try {
        Color(android.graphics.Color.parseColor(genre.color))
    } catch (_: Exception) {
        NyasaSurface3
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = genre.name,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun GenreSongsSection(
    genre: Genre,
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onMoreClick: (Song) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Text(
                text = genre.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No songs in this genre",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NyasaTextSecondary,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(songs, key = { it.mediaId }) { song ->
                    SearchResultRow(
                        song = song,
                        onClick = { onSongClick(song) },
                        onMoreClick = { onMoreClick(song) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<Song>,
    onSongClick: (Song) -> Unit,
    onMoreClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No results found",
                style = MaterialTheme.typography.bodyLarge,
                color = NyasaTextSecondary,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            items(results, key = { it.mediaId }) { song ->
                SearchResultRow(
                    song = song,
                    onClick = { onSongClick(song) },
                    onMoreClick = { onMoreClick(song) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    song: Song,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.resolvedCoverUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
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
private fun SearchScreenPreview() {
    AppTheme {
        SearchContent(
            uiState = SearchUiState(
                isLoading = false,
                genres = PreviewGenres,
            ),
            onQueryChange = { },
            onSongClick = { _, _ -> },
            onGenreClick = { },
            onGenreBack = { },
            onToggleLike = { },
            isLikedProvider = { flowOf(false) },
            downloadManager = null,
        )
    }
}
