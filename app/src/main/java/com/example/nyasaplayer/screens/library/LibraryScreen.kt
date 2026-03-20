package com.example.nyasaplayer.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.nyasaplayer.R
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.CreatePlaylistDialog
import com.example.nyasaplayer.core.common.ui.components.NyasaErrorScreen
import com.example.nyasaplayer.core.common.ui.components.PlaylistPickerSheet
import com.example.nyasaplayer.core.common.ui.components.ShufflePlayButton
import com.example.nyasaplayer.core.common.ui.icons.HeartIcon
import com.example.nyasaplayer.core.common.ui.icons.PlaylistAddIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.download.SongDownloadManager
import com.example.nyasaplayer.screens.common.SongOverflowWithDownload
import com.example.nyasaplayer.screens.common.SongRow
import com.example.nyasaplayer.screens.playlist.PlaylistViewModel

@Composable
fun LibraryScreen(
    onSongClick: (List<Song>, Song) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isCurrentlyPlaying: Boolean = false,
    downloadManager: SongDownloadManager? = null,
    onPlaylistClick: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState()
    val playlistCovers by playlistViewModel.playlistCovers.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        playlistViewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(modifier = modifier) {
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
            )
            else -> LibraryContent(
                songs = uiState.songs,
                currentlyPlayingMediaId = currentlyPlayingMediaId,
                isCurrentlyPlaying = isCurrentlyPlaying,
                onSongClick = onSongClick,
                onShufflePlay = onShufflePlay,
                onToggleLike = viewModel::removeLikedSong,
                downloadManager = downloadManager,
                playlists = playlists,
                playlistCovers = playlistCovers,
                onPlaylistClick = onPlaylistClick,
                onAddToPlaylist = playlistViewModel::addSongToPlaylist,
                onCreatePlaylistAndAdd = playlistViewModel::createPlaylistAndAddSong,
                onRemoveFromPlaylist = playlistViewModel::removeSongFromPlaylist,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun LibraryContent(
    songs: List<Song>,
    currentlyPlayingMediaId: String?,
    isCurrentlyPlaying: Boolean,
    onSongClick: (List<Song>, Song) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onToggleLike: (String) -> Unit,
    downloadManager: SongDownloadManager?,
    playlists: List<Playlist>,
    playlistCovers: Map<String, List<String>>,
    onPlaylistClick: (String) -> Unit,
    onAddToPlaylist: (String, String) -> Unit,
    onCreatePlaylistAndAdd: (String, String) -> Unit,
    onRemoveFromPlaylist: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createDialogSongId by remember { mutableStateOf("") }
    var removalPickerSong by remember { mutableStateOf<Song?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        LibraryHeader(songCount = songs.size)
        Spacer(modifier = Modifier.height(12.dp))
        FilterChips()
        if (playlists.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            SavedPlaylistsRow(
                playlists = playlists,
                playlistCovers = playlistCovers,
                onPlaylistClick = onPlaylistClick,
            )
        }
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
                SongRow(
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

    LibraryOverflowDialogs(
        selectedSong = selectedSong,
        playlistPickerSong = playlistPickerSong,
        showCreateDialog = showCreateDialog,
        downloadManager = downloadManager,
        playlists = playlists,
        onDismissOverflow = { selectedSong = null },
        onToggleLike = { mediaId ->
            selectedSong = null
            onToggleLike(mediaId)
        },
        onOpenPlaylistPicker = { song ->
            selectedSong = null
            playlistPickerSong = song
        },
        onDismissPlaylistPicker = { playlistPickerSong = null },
        onAddToPlaylist = { playlistId, mediaId ->
            playlistPickerSong = null
            onAddToPlaylist(playlistId, mediaId)
        },
        onOpenCreateDialog = { songId ->
            playlistPickerSong = null
            createDialogSongId = songId
            showCreateDialog = true
        },
        onDismissCreateDialog = { showCreateDialog = false },
        onCreatePlaylistAndAdd = { name ->
            showCreateDialog = false
            onCreatePlaylistAndAdd(name, createDialogSongId)
        },
        onOpenRemovalPicker = { song ->
            selectedSong = null
            removalPickerSong = song
        },
    )

    removalPickerSong?.let { song ->
        val containingPlaylists = playlists.filter { song.mediaId in it.songIds }
        PlaylistPickerSheet(
            playlists = containingPlaylists,
            onSelectPlaylist = { playlist ->
                removalPickerSong = null
                onRemoveFromPlaylist(playlist.id, song.mediaId)
            },
            onDismiss = { removalPickerSong = null },
            title = stringResource(R.string.remove_from_playlist),
            showCreateNew = false,
        )
    }
}

@Composable
private fun LibraryOverflowDialogs(
    selectedSong: Song?,
    playlistPickerSong: Song?,
    showCreateDialog: Boolean,
    downloadManager: SongDownloadManager?,
    playlists: List<Playlist>,
    onDismissOverflow: () -> Unit,
    onToggleLike: (String) -> Unit,
    onOpenPlaylistPicker: (Song) -> Unit,
    onDismissPlaylistPicker: () -> Unit,
    onAddToPlaylist: (String, String) -> Unit,
    onOpenCreateDialog: (String) -> Unit,
    onDismissCreateDialog: () -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
    onOpenRemovalPicker: (Song) -> Unit,
    @Suppress("UnusedParameter") modifier: Modifier = Modifier,
) {
    selectedSong?.let { song ->
        val isInAnyPlaylist = playlists.any { song.mediaId in it.songIds }
        SongOverflowWithDownload(
            song = song,
            downloadManager = downloadManager,
            onDismiss = onDismissOverflow,
            isLiked = true,
            onToggleLike = { onToggleLike(song.mediaId) },
            onSaveToPlaylist = { onOpenPlaylistPicker(song) },
            showRemoveFromPlaylist = isInAnyPlaylist,
            onRemoveFromPlaylist = { onOpenRemovalPicker(song) },
        )
    }

    playlistPickerSong?.let { song ->
        PlaylistPickerSheet(
            playlists = playlists,
            onSelectPlaylist = { playlist -> onAddToPlaylist(playlist.id, song.mediaId) },
            onCreateNew = { onOpenCreateDialog(song.mediaId) },
            onDismiss = onDismissPlaylistPicker,
        )
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onConfirm = onCreatePlaylistAndAdd,
            onDismiss = onDismissCreateDialog,
        )
    }
}

@Composable
private fun SavedPlaylistsRow(
    playlists: List<Playlist>,
    playlistCovers: Map<String, List<String>>,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.saved_playlists),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    name = playlist.name,
                    songCount = playlist.songIds.size,
                    coverUrls = playlistCovers[playlist.id].orEmpty(),
                    onClick = { onPlaylistClick(playlist.id) },
                )
            }
        }
    }
}

private const val PlaylistCardSize = 120

@Composable
private fun PlaylistCard(
    name: String,
    songCount: Int,
    coverUrls: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(PlaylistCardSize.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(PlaylistCardSize.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                coverUrls.size >= CoverGridMinCount -> CoverGrid(coverUrls = coverUrls)
                coverUrls.isNotEmpty() -> AsyncImage(
                    model = coverUrls.first(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = PlaylistAddIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.playlist_song_count, songCount),
            style = MaterialTheme.typography.bodySmall,
            color = NyasaTextSecondary,
        )
    }
}

private const val CoverGridMinCount = 4

@Composable
private fun CoverGrid(
    coverUrls: List<String>,
    modifier: Modifier = Modifier,
) {
    require(coverUrls.size >= CoverGridMinCount) { "CoverGrid requires at least $CoverGridMinCount URLs" }
    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f)) {
            AsyncImage(
                model = coverUrls[0],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            AsyncImage(
                model = coverUrls[1],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(modifier = Modifier.weight(1f)) {
            AsyncImage(
                model = coverUrls[2],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            AsyncImage(
                model = coverUrls[3],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
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
