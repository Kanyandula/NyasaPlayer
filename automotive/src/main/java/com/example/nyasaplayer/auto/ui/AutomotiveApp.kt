package com.example.nyasaplayer.auto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.example.nyasaplayer.auto.ui.components.CarErrorOverlay
import com.example.nyasaplayer.auto.ui.components.CarMiniPlayer
import com.example.nyasaplayer.auto.ui.components.CarTopBar
import com.example.nyasaplayer.auto.ui.navigation.CarScreen
import com.example.nyasaplayer.auto.ui.screens.CarBrowseScreen
import com.example.nyasaplayer.auto.ui.screens.CarFullPlayerScreen
import com.example.nyasaplayer.auto.ui.screens.CarHomeScreen
import com.example.nyasaplayer.auto.ui.screens.CarLibraryScreen
import com.example.nyasaplayer.auto.viewmodel.AutomotiveContentState
import com.example.nyasaplayer.auto.viewmodel.AutomotiveContentViewModel
import com.example.nyasaplayer.auto.viewmodel.AutomotivePlayerViewModel
import com.example.nyasaplayer.auto.viewmodel.AutomotiveUiState
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import kotlinx.coroutines.launch

@UnstableApi
@Suppress("LongMethod")
@Composable
fun AutomotiveApp(
    modifier: Modifier = Modifier,
    playerViewModel: AutomotivePlayerViewModel = hiltViewModel(),
    contentViewModel: AutomotiveContentViewModel = hiltViewModel(),
) {
    val playerState by playerViewModel.uiState.collectAsState()
    val contentState by contentViewModel.contentState.collectAsState()

    var currentScreen by rememberSaveable { mutableStateOf(CarScreen.Home) }
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground),
    ) {
        if (showFullPlayer) {
            CarFullPlayerScreen(
                playback = playerState.playback,
                onCollapseClick = { showFullPlayer = false },
                onPlayPauseClick = playerViewModel::togglePlayPause,
                onSkipNextClick = playerViewModel::skipNext,
                onSkipPreviousClick = playerViewModel::skipPrevious,
                onShuffleClick = playerViewModel::toggleShuffle,
                onRepeatClick = playerViewModel::toggleRepeatMode,
                onSeek = playerViewModel::seekTo,
            )
        } else {
            BrowseShell(
                currentScreen = currentScreen,
                playerState = playerState,
                contentState = contentState,
                onSelectTab = { currentScreen = it },
                onExpandPlayer = { showFullPlayer = true },
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onSkipNext = playerViewModel::skipNext,
                onSkipPrevious = playerViewModel::skipPrevious,
                onSongClick = { song ->
                    playerViewModel.playSong(contentState.recentlyPlayed, song)
                    showFullPlayer = true
                },
                onQuickActionClick = { /* Phase 7 — not critical for playback */ },
                onGenreClick = { genre ->
                    scope.launch {
                        val songs = contentViewModel.getSongsByGenre(genre.id)
                        if (songs.isNotEmpty()) {
                            playerViewModel.playSong(songs, songs.first())
                            showFullPlayer = true
                        }
                    }
                },
                onAlbumClick = { album ->
                    scope.launch {
                        val songs = contentViewModel.getSongsByAlbum(album.id)
                        if (songs.isNotEmpty()) {
                            playerViewModel.playSong(songs, songs.first())
                            showFullPlayer = true
                        }
                    }
                },
                onArtistClick = { /* Phase 7 — artist detail screen */ },
            )
        }

        val error = playerState.error
        if (error != null) {
            CarErrorOverlay(
                error = error,
                onDismiss = playerViewModel::clearError,
                onRetry = {
                    playerViewModel.clearError()
                    playerViewModel.togglePlayPause()
                },
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun BrowseShell(
    currentScreen: CarScreen,
    playerState: AutomotiveUiState,
    contentState: AutomotiveContentState,
    onSelectTab: (CarScreen) -> Unit,
    onExpandPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSongClick: (com.example.nyasaplayer.core.common.models.Song) -> Unit,
    onQuickActionClick: (String) -> Unit,
    onGenreClick: (com.example.nyasaplayer.core.common.models.Genre) -> Unit,
    onAlbumClick: (com.example.nyasaplayer.core.common.models.Album) -> Unit,
    onArtistClick: (com.example.nyasaplayer.core.common.models.Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        CarTopBar(currentScreen = currentScreen, onSelectTab = onSelectTab)

        Box(modifier = Modifier.weight(1f)) {
            when (currentScreen) {
                CarScreen.Home -> CarHomeScreen(
                    recentlyPlayed = contentState.recentlyPlayed,
                    onSongClick = onSongClick,
                    onQuickActionClick = onQuickActionClick,
                )

                CarScreen.Browse -> CarBrowseScreen(
                    genres = contentState.genres,
                    albums = contentState.albums,
                    onGenreClick = onGenreClick,
                    onAlbumClick = onAlbumClick,
                )

                CarScreen.Library -> CarLibraryScreen(
                    artists = contentState.artists,
                    albums = contentState.albums,
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick,
                )

                CarScreen.FullPlayer -> Unit
            }
        }

        if (playerState.playback.currentSong != null) {
            CarMiniPlayer(
                playback = playerState.playback,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onExpand = onExpandPlayer,
            )
        }
    }
}
