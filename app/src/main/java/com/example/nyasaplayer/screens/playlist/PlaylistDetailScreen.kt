package com.example.nyasaplayer.screens.playlist

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nyasaplayer.R
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.CreatePlaylistDialog
import com.example.nyasaplayer.core.common.ui.components.NyasaErrorScreen
import com.example.nyasaplayer.core.common.ui.components.PlaylistPickerSheet
import com.example.nyasaplayer.core.common.ui.components.ShufflePlayButton
import com.example.nyasaplayer.core.common.ui.icons.PlaylistAddIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextTertiary
import com.example.nyasaplayer.download.SongDownloadManager
import com.example.nyasaplayer.screens.common.SongOverflowWithDownload
import com.example.nyasaplayer.screens.common.SongRow
import kotlinx.coroutines.flow.merge

@Composable
fun PlaylistDetailScreen(
    onSongClick: (List<Song>, Song) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isCurrentlyPlaying: Boolean = false,
    downloadManager: SongDownloadManager? = null,
    detailViewModel: PlaylistDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
) {
    val uiState by detailViewModel.uiState.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        merge(detailViewModel.snackbarMessage, playlistViewModel.snackbarMessage)
            .collect { message ->
                snackbarHostState.showSnackbar(message)
            }
    }

    Box(modifier = modifier) {
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = NyasaPrimary)
            }
            uiState.errorMessage != null -> NyasaErrorScreen(
                title = stringResource(R.string.error_generic_title),
                subtitle = stringResource(R.string.error_generic_subtitle),
                onRetry = detailViewModel::retry,
                buttonText = stringResource(R.string.try_again),
            )
            else -> PlaylistDetailContent(
                playlistName = uiState.playlistName,
                songs = uiState.songs,
                currentlyPlayingMediaId = currentlyPlayingMediaId,
                isCurrentlyPlaying = isCurrentlyPlaying,
                onSongClick = onSongClick,
                onShufflePlay = onShufflePlay,
                onBack = onBack,
                onRemoveSong = detailViewModel::removeSong,
                downloadManager = downloadManager,
                playlists = playlists,
                onAddToPlaylist = playlistViewModel::addSongToPlaylist,
                onCreatePlaylistAndAdd = playlistViewModel::createPlaylistAndAddSong,
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
private fun PlaylistDetailContent(
    playlistName: String,
    songs: List<Song>,
    currentlyPlayingMediaId: String?,
    isCurrentlyPlaying: Boolean,
    onSongClick: (List<Song>, Song) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onBack: () -> Unit,
    onRemoveSong: (String) -> Unit,
    downloadManager: SongDownloadManager?,
    playlists: List<Playlist>,
    onAddToPlaylist: (String, String) -> Unit,
    onCreatePlaylistAndAdd: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createDialogSongId by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        PlaylistDetailHeader(
            playlistName = playlistName,
            songCount = songs.size,
            onBack = onBack,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (songs.isNotEmpty()) {
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
        } else {
            EmptyPlaylistMessage(modifier = Modifier.weight(1f))
        }
    }

    selectedSong?.let { song ->
        SongOverflowWithDownload(
            song = song,
            downloadManager = downloadManager,
            onDismiss = { selectedSong = null },
            onSaveToPlaylist = {
                selectedSong = null
                playlistPickerSong = song
            },
            showRemoveFromPlaylist = true,
            onRemoveFromPlaylist = {
                selectedSong = null
                onRemoveSong(song.mediaId)
            },
        )
    }

    playlistPickerSong?.let { song ->
        PlaylistPickerSheet(
            playlists = playlists,
            onSelectPlaylist = { playlist ->
                playlistPickerSong = null
                onAddToPlaylist(playlist.id, song.mediaId)
            },
            onCreateNew = {
                playlistPickerSong = null
                createDialogSongId = song.mediaId
                showCreateDialog = true
            },
            onDismiss = { playlistPickerSong = null },
        )
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onConfirm = { name ->
                showCreateDialog = false
                onCreatePlaylistAndAdd(name, createDialogSongId)
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
private fun PlaylistDetailHeader(
    playlistName: String,
    songCount: Int,
    onBack: () -> Unit,
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
            imageVector = PlaylistAddIcon,
            contentDescription = null,
            tint = NyasaPrimary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlistName,
                style = MaterialTheme.typography.headlineLarge,
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
}

@Composable
private fun EmptyPlaylistMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = PlaylistAddIcon,
                contentDescription = null,
                tint = NyasaTextTertiary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_songs_in_playlist),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}
