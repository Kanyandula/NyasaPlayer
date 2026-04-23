package com.example.nyasaplayer.auto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.nyasaplayer.auto.ui.screens.CarArtistLikedSongsScreen
import com.example.nyasaplayer.auto.ui.screens.CarAuthScreen
import com.example.nyasaplayer.auto.ui.screens.CarBrowseScreen
import com.example.nyasaplayer.auto.ui.screens.CarFullPlayerScreen
import com.example.nyasaplayer.auto.ui.screens.CarHomeScreen
import com.example.nyasaplayer.auto.ui.screens.CarLibraryScreen
import com.example.nyasaplayer.auto.ui.screens.CarQueueScreen
import com.example.nyasaplayer.auto.viewmodel.AutomotiveAuthViewModel
import com.example.nyasaplayer.auto.viewmodel.AutomotiveContentState
import com.example.nyasaplayer.auto.viewmodel.AutomotiveContentViewModel
import com.example.nyasaplayer.auto.viewmodel.AutomotivePlayerViewModel
import com.example.nyasaplayer.auto.viewmodel.AutomotiveUiState
import com.example.nyasaplayer.auto.viewmodel.FavoriteArtist
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.OfflineBanner
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun AutomotiveApp(
    modifier: Modifier = Modifier,
    authViewModel: AutomotiveAuthViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()

    if (authState.isAuthenticated) {
        AuthenticatedApp(
            onSignOut = authViewModel::signOut,
            userDisplayName = authViewModel.currentUserDisplayName,
            modifier = modifier,
        )
    } else {
        CarAuthScreen(
            uiState = authState,
            onGoogleToken = authViewModel::signInWithGoogleToken,
            onGoogleError = authViewModel::onGoogleSignInError,
            modifier = modifier,
        )
    }
}

@UnstableApi
@Suppress("LongMethod")
@Composable
private fun AuthenticatedApp(
    onSignOut: () -> Unit,
    userDisplayName: String,
    modifier: Modifier = Modifier,
    playerViewModel: AutomotivePlayerViewModel = hiltViewModel(),
    contentViewModel: AutomotiveContentViewModel = hiltViewModel(),
) {
    val playerState by playerViewModel.uiState.collectAsState()
    val contentState by contentViewModel.contentState.collectAsState()

    LaunchedEffect(Unit) {
        contentViewModel.reloadUserContent()
    }

    var currentScreen by rememberSaveable { mutableStateOf(CarScreen.Home) }
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var selectedArtist by rememberSaveable { mutableStateOf<FavoriteArtist?>(null) }
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
                isLiked = playerState.isCurrentSongLiked,
                onLikeClick = playerViewModel::toggleLike,
                onQueueClick = { showQueue = true },
            )
        } else {
            BrowseShell(
                currentScreen = currentScreen,
                playerState = playerState,
                contentState = contentState,
                onSignOut = onSignOut,
                userDisplayName = userDisplayName,
                onSelectTab = {
                    selectedArtist = null
                    currentScreen = it
                },
                onExpandPlayer = { showFullPlayer = true },
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onSkipNext = playerViewModel::skipNext,
                onSkipPrevious = playerViewModel::skipPrevious,
                onSongClick = { song ->
                    playerViewModel.playSong(contentState.recentlyPlayed, song)
                    showFullPlayer = true
                },
                onQuickActionClick = { action ->
                    when (action) {
                        "my_music", "favorites" -> currentScreen = CarScreen.Library
                        "trending", "radio" -> currentScreen = CarScreen.Browse
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
                onArtistClick = { favoriteArtist -> selectedArtist = favoriteArtist },
                selectedArtist = selectedArtist,
                onBackFromArtist = { selectedArtist = null },
                onArtistSongClick = { songs, song ->
                    playerViewModel.playSong(songs, song)
                    showFullPlayer = true
                },
                onShuffleArtistSongs = { songs ->
                    playerViewModel.shufflePlay(songs)
                    showFullPlayer = true
                },
                onCategoryClick = { categoryName ->
                    scope.launch {
                        val genre = contentState.genres.firstOrNull {
                            it.name.equals(categoryName, ignoreCase = true)
                        } ?: return@launch
                        val songs = contentViewModel.getSongsByGenre(genre.id)
                        if (songs.isNotEmpty()) {
                            playerViewModel.shufflePlay(songs)
                            showFullPlayer = true
                        }
                    }
                },
                onLikeClick = playerViewModel::toggleLike,
                onShuffleLikedSongs = { playerViewModel.shufflePlay(contentState.likedSongs) },
                onLikedSongClick = { song ->
                    playerViewModel.playSong(contentState.likedSongs, song)
                    showFullPlayer = true
                },
                onSearchQueryChange = contentViewModel::onSearchQueryChange,
                onClearSearch = contentViewModel::clearSearch,
                onSearchResultClick = { song ->
                    playerViewModel.playSong(contentState.searchResults, song)
                    showFullPlayer = true
                },
            )
        }

        if (showQueue) {
            CarQueueScreen(
                queue = playerState.playback.queue,
                currentIndex = playerState.playback.currentQueueIndex,
                isPlaying = playerState.playback.isPlaying,
                isDriving = playerState.restrictions.isDistractionOptimized,
                onCloseClick = { showQueue = false },
                onSkipTo = playerViewModel::skipToQueueItem,
                onRemove = playerViewModel::removeFromQueue,
                onClearQueue = playerViewModel::clearQueue,
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

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun BrowseShell(
    currentScreen: CarScreen,
    playerState: AutomotiveUiState,
    contentState: AutomotiveContentState,
    onSignOut: () -> Unit,
    userDisplayName: String,
    onSelectTab: (CarScreen) -> Unit,
    onExpandPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSongClick: (Song) -> Unit,
    onQuickActionClick: (String) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (FavoriteArtist) -> Unit,
    selectedArtist: FavoriteArtist?,
    onBackFromArtist: () -> Unit,
    onArtistSongClick: (List<Song>, Song) -> Unit,
    onShuffleArtistSongs: (List<Song>) -> Unit,
    onLikeClick: () -> Unit,
    onShuffleLikedSongs: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onLikedSongClick: (Song) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchResultClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentlyPlayingMediaId = playerState.playback.currentSong?.mediaId
    val isPlaying = playerState.playback.isPlaying
    val maxItems = playerState.restrictions.limitedContentItems

    Column(modifier = modifier.fillMaxSize()) {
        CarTopBar(currentScreen = currentScreen, onSelectTab = onSelectTab)

        OfflineBanner(isOffline = playerState.isOffline)

        Box(modifier = Modifier.weight(1f)) {
            when (currentScreen) {
                CarScreen.Home -> CarHomeScreen(
                    recentlyPlayed = contentState.recentlyPlayed.take(maxItems),
                    onSongClick = onSongClick,
                    onQuickActionClick = onQuickActionClick,
                    currentlyPlayingMediaId = currentlyPlayingMediaId,
                    isPlaying = isPlaying,
                )

                CarScreen.Browse -> {
                    val currentAlbumId = playerState.playback.currentSong?.albumId
                    CarBrowseScreen(
                        albums = contentState.albums.take(maxItems),
                        onAlbumClick = onAlbumClick,
                        currentlyPlayingAlbumId = currentAlbumId,
                        currentlyPlayingMediaId = currentlyPlayingMediaId,
                        isPlaying = isPlaying,
                        onCategoryClick = onCategoryClick,
                        searchQuery = contentState.searchQuery,
                        searchResults = contentState.searchResults.take(maxItems),
                        onSearchQueryChange = onSearchQueryChange,
                        onClearSearch = onClearSearch,
                        onSearchResultClick = onSearchResultClick,
                        isSearchDisabled = playerState.restrictions.isDistractionOptimized,
                    )
                }

                CarScreen.Library -> if (selectedArtist != null) {
                    val artistLikedSongs = remember(
                        contentState.likedSongs,
                        selectedArtist.artistId,
                        maxItems,
                    ) {
                        contentState.likedSongs
                            .filter { it.artistId == selectedArtist.artistId }
                            .take(maxItems)
                    }
                    CarArtistLikedSongsScreen(
                        artistName = selectedArtist.artistName,
                        likedSongs = artistLikedSongs,
                        onBackClick = onBackFromArtist,
                        onSongClick = { song -> onArtistSongClick(artistLikedSongs, song) },
                        onShufflePlay = { onShuffleArtistSongs(artistLikedSongs) },
                        currentlyPlayingMediaId = currentlyPlayingMediaId,
                        isPlaying = isPlaying,
                    )
                } else {
                    CarLibraryScreen(
                        favoriteArtists = contentState.favoriteArtists.take(maxItems),
                        albums = contentState.albums.take(maxItems),
                        onArtistClick = onArtistClick,
                        onAlbumClick = onAlbumClick,
                        likedSongs = contentState.likedSongs.take(maxItems),
                        currentlyPlayingMediaId = currentlyPlayingMediaId,
                        isPlaying = isPlaying,
                        onShuffleLikedSongs = onShuffleLikedSongs,
                        onLikedSongClick = onLikedSongClick,
                        onSignOut = onSignOut,
                        userDisplayName = userDisplayName,
                    )
                }
            }
        }

        if (playerState.playback.currentSong != null) {
            CarMiniPlayer(
                playback = playerState.playback,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onExpand = onExpandPlayer,
                isLiked = playerState.isCurrentSongLiked,
                onLikeClick = onLikeClick,
            )
        }
    }
}
