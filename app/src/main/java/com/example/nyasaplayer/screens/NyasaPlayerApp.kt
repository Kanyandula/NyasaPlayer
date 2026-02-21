package com.example.nyasaplayer.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import com.example.nyasaplayer.navigation.NyasaBottomNavBar
import com.example.nyasaplayer.navigation.NyasaPlayerNavHost
import com.example.nyasaplayer.player.PlayerMode
import com.example.nyasaplayer.player.PlayerViewModel
import com.example.nyasaplayer.screens.player.GlobalPlayerLayer
import com.example.nyasaplayer.ui.theme.NyasaSurface3

@UnstableApi
@Composable
fun NyasaPlayerApp(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val playerState by playerViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        playerViewModel.restorePlaybackState()
    }

    LaunchedEffect(playerState.errorMessage, playerState.playerMode) {
        val message = playerState.errorMessage ?: return@LaunchedEffect
        if (playerState.playerMode != PlayerMode.Expanded) {
            snackbarHostState.showSnackbar(message)
            playerViewModel.clearError()
        }
    }

    val density = LocalDensity.current
    var navBarHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { AppSnackbarHost(snackbarHostState) },
            bottomBar = {
                NyasaBottomNavBar(
                    navController = navController,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        navBarHeight = with(density) { coordinates.size.height.toDp() }
                    },
                )
            },
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ) { innerPadding ->
            NyasaPlayerNavHost(
                navController = navController,
                onSongClick = { songs, song -> playerViewModel.playSong(songs, song) },
                onShufflePlay = playerViewModel::shufflePlay,
                onSignOut = onSignOut,
                currentlyPlayingMediaId = playerState.currentSong?.mediaId,
                modifier = Modifier.padding(innerPadding),
            )
        }

        GlobalPlayerLayer(
            state = playerState,
            onTogglePlayPause = playerViewModel::togglePlayPause,
            onSeek = playerViewModel::seekTo,
            onSkipPrevious = playerViewModel::skipPrevious,
            onSkipNext = playerViewModel::skipNext,
            onExpand = playerViewModel::expand,
            onCollapse = playerViewModel::collapse,
            onDismiss = playerViewModel::dismiss,
            onToggleRepeatMode = playerViewModel::toggleRepeatMode,
            onToggleLike = playerViewModel::toggleLike,
            onToggleShuffle = playerViewModel::toggleShuffle,
            onClearError = playerViewModel::clearError,
            bottomOffset = navBarHeight,
        )
    }
}

@Composable
private fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = NyasaSurface3,
            contentColor = Color.White,
        )
    }
}
