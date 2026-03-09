package com.example.nyasaplayer.auto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

@UnstableApi
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        CarTopBar(currentScreen = currentScreen, onSelectTab = onSelectTab)

        Box(modifier = Modifier.weight(1f)) {
            when (currentScreen) {
                CarScreen.Home -> CarHomeScreen(
                    recentlyPlayed = contentState.recentlyPlayed,
                    onSongClick = { /* TODO: play song */ },
                    onQuickActionClick = { /* TODO: handle quick action */ },
                )

                CarScreen.Browse -> CarBrowseScreen(
                    genres = contentState.genres,
                    albums = contentState.albums,
                    onGenreClick = { /* TODO: play genre */ },
                    onAlbumClick = { /* TODO: play album */ },
                )

                CarScreen.Library -> CarLibraryScreen(
                    artists = contentState.artists,
                    albums = contentState.albums,
                    onArtistClick = { /* TODO: show artist */ },
                    onAlbumClick = { /* TODO: play album */ },
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
