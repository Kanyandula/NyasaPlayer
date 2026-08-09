package com.example.nyasaplayer.auto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.example.nyasaplayer.auto.ui.components.CarAmbientBackground
import com.example.nyasaplayer.auto.ui.components.CarErrorOverlay
import com.example.nyasaplayer.auto.ui.components.CarMiniPlayer
import com.example.nyasaplayer.auto.ui.components.CarNavRail
import com.example.nyasaplayer.auto.ui.components.CarRestrictionDialog
import com.example.nyasaplayer.auto.ui.components.CarSystemBar
import com.example.nyasaplayer.auto.ui.motion.decorativeMotionEnabled
import com.example.nyasaplayer.auto.ui.motion.rememberAnimatorDurationScale
import com.example.nyasaplayer.auto.ui.navigation.CarDestination
import com.example.nyasaplayer.auto.ui.navigation.CarOverlay
import com.example.nyasaplayer.auto.ui.navigation.CarScreen
import com.example.nyasaplayer.auto.ui.navigation.CarUiLocation
import com.example.nyasaplayer.auto.ui.navigation.GateResult
import com.example.nyasaplayer.auto.ui.navigation.gate
import com.example.nyasaplayer.auto.ui.screens.CarAlbumScreen
import com.example.nyasaplayer.auto.ui.screens.CarArtistLikedSongsScreen
import com.example.nyasaplayer.auto.ui.screens.CarAuthScreen
import com.example.nyasaplayer.auto.ui.screens.CarBrowseScreen
import com.example.nyasaplayer.auto.ui.screens.CarFavouriteMusicScreen
import com.example.nyasaplayer.auto.ui.screens.CarFullPlayerScreen
import com.example.nyasaplayer.auto.ui.screens.CarHomeScreen
import com.example.nyasaplayer.auto.ui.screens.CarLibraryScreen
import com.example.nyasaplayer.auto.ui.screens.CarPlaylistScreen
import com.example.nyasaplayer.auto.ui.screens.CarQueueScreen
import com.example.nyasaplayer.auto.ui.theme.CarScreenMargin
import com.example.nyasaplayer.auto.viewmodel.AutomotiveAuthViewModel
import com.example.nyasaplayer.auto.viewmodel.AutomotiveContentState
import com.example.nyasaplayer.auto.viewmodel.AutomotiveContentViewModel
import com.example.nyasaplayer.auto.viewmodel.AutomotivePlayerViewModel
import com.example.nyasaplayer.auto.viewmodel.AutomotiveUiState
import com.example.nyasaplayer.auto.viewmodel.CarDetailState
import com.example.nyasaplayer.auto.viewmodel.FavoriteArtist
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.Playlist
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

    // The background is painted once, here, rather than by each screen — the ambient layer
    // sits above it and would be hidden by any opaque surface drawn on top. It has to be at
    // the root and not in the shell, because the auth branch never passes through the shell.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground),
    ) {
        if (authState.isAuthenticated) {
            AuthenticatedApp(
                onSignOut = authViewModel::signOut,
                userDisplayName = authViewModel.currentUserDisplayName,
            )
        } else {
            CarAuthScreen(
                uiState = authState,
                onGoogleToken = authViewModel::signInWithGoogleToken,
                onGoogleError = authViewModel::onGoogleSignInError,
            )
        }
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
    var drillDown by rememberSaveable { mutableStateOf<CarDestination?>(null) }
    var denialReason by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val location = carUiLocation(
        tab = currentScreen,
        showFullPlayer = showFullPlayer,
        showQueue = showQueue,
        drillDown = drillDown,
        searchQuery = contentState.searchQuery,
    )

    // Keyed on both the restrictions and the location so it fires when the vehicle starts
    // moving *and* when the user navigates. Gating entry alone is not enough — a vehicle
    // can start moving while the driver is already inside a restricted screen.
    LaunchedEffect(playerState.restrictions, location) {
        when (val result = gate(location, playerState.restrictions)) {
            is GateResult.Allowed -> Unit
            is GateResult.Denied -> {
                showFullPlayer = false
                showQueue = false
                drillDown = null
                currentScreen = result.evictTo.tab
                denialReason = result.reason
            }
        }
    }

    // One entry point. openDetail early-returns for Artist, whose tracks are a live filter over
    // likedSongs rather than a snapshot (D16).
    LaunchedEffect(drillDown) {
        val destination = drillDown
        if (destination != null) {
            contentViewModel.openDetail(destination)
        } else {
            contentViewModel.closeDetail()
        }
    }

    val animatorScale by rememberAnimatorDurationScale()
    val motionEnabled = decorativeMotionEnabled(
        isDistractionOptimized = playerState.restrictions.isDistractionOptimized,
        animatorScale = animatorScale,
    )

    // No background here: the root paints it, and an opaque surface at this level would
    // hide the ambient layer. This Box exists only to stack the overlays.
    Box(modifier = modifier.fillMaxSize()) {
        CarAmbientBackground(animate = motionEnabled)

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
                    drillDown = null
                    currentScreen = it
                },
                onExpandPlayer = { showFullPlayer = true },
                onQueueClick = { showQueue = true },
                decorativeMotionEnabled = motionEnabled,
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onSkipNext = playerViewModel::skipNext,
                onSkipPrevious = playerViewModel::skipPrevious,
                onSongClick = { songs, song ->
                    playerViewModel.playSong(songs, song)
                    showFullPlayer = true
                },
                onRetry = contentViewModel::retryLoad,
                onAlbumClick = { album -> drillDown = CarDestination.Album(album.id) },
                onPlaylistClick = { playlist -> drillDown = CarDestination.Playlist(playlist.id) },
                onArtistClick = { favoriteArtist ->
                    drillDown = CarDestination.Artist(
                        artistId = favoriteArtist.artistId,
                        artistName = favoriteArtist.artistName,
                    )
                },
                drillDown = drillDown,
                onBackFromDetail = { drillDown = null },
                onArtistSongClick = { songs, song ->
                    playerViewModel.playSong(songs, song)
                    showFullPlayer = true
                },
                onShuffleTracks = { songs ->
                    playerViewModel.shufflePlay(songs)
                    showFullPlayer = true
                },
                onPlayTracks = { tracks ->
                    tracks.firstOrNull()?.let { first ->
                        playerViewModel.playSong(tracks, first)
                        showFullPlayer = true
                    }
                },
                onGenreClick = { genre ->
                    scope.launch {
                        val songs = contentViewModel.getSongsByGenre(genre.id)
                        if (songs.isNotEmpty()) {
                            playerViewModel.shufflePlay(songs)
                            showFullPlayer = true
                        }
                    }
                },
                onLikeClick = playerViewModel::toggleLike,
                onLikedSongClick = { song ->
                    playerViewModel.playSong(contentState.likedSongs, song)
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

        val reason = denialReason
        if (reason != null) {
            CarRestrictionDialog(
                reason = reason,
                onDismiss = { denialReason = null },
            )
        }
    }
}

/**
 * Collapses the five scattered pieces of navigation state into the one value [gate] decides
 * on. Derived, not authoritative — the individual values stay where the screens read them.
 */
private fun carUiLocation(
    tab: CarScreen,
    showFullPlayer: Boolean,
    showQueue: Boolean,
    drillDown: CarDestination?,
    searchQuery: String,
): CarUiLocation = CarUiLocation(
    tab = tab,
    overlay = when {
        showFullPlayer -> CarOverlay.FullPlayer
        showQueue -> CarOverlay.Queue
        else -> null
    },
    // All three destinations are one step from a tab root (D8). There is no depth 2 in A3.
    drillDepth = if (drillDown != null) 1 else 0,
    // Settings and Profile are later slices, and Search is not yet a distinct sheet in this
    // app. The field exists so those slices have nothing to retrofit.
    sheet = null,
    textEntryActive = searchQuery.isNotEmpty(),
)

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
    onQueueClick: () -> Unit,
    decorativeMotionEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    onRetry: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (FavoriteArtist) -> Unit,
    drillDown: CarDestination?,
    onBackFromDetail: () -> Unit,
    onArtistSongClick: (List<Song>, Song) -> Unit,
    onShuffleTracks: (List<Song>) -> Unit,
    onPlayTracks: (List<Song>) -> Unit,
    onLikeClick: () -> Unit,
    onGenreClick: (Genre) -> Unit,
    onLikedSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentlyPlayingMediaId = playerState.playback.currentSong?.mediaId
    val isPlaying = playerState.playback.isPlaying
    val maxItems = playerState.restrictions.maxCumulativeContentItems

    Column(modifier = modifier.fillMaxSize()) {
        // The callbacks are no-ops until A6/A7 give search, settings and profile somewhere
        // to go; the controls render disabled in the meantime.
        CarSystemBar(onSearchClick = {}, onSettingsClick = {}, onAvatarClick = {})

        // Above the rail: an app-level condition, not screen content.
        OfflineBanner(isOffline = playerState.isOffline)

        Row(modifier = Modifier.weight(1f)) {
            CarNavRail(
                currentScreen = currentScreen,
                onSelectTab = onSelectTab,
                animateSelection = decorativeMotionEnabled,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(CarScreenMargin),
            ) {
                when (currentScreen) {
                    CarScreen.Home -> CarHomeScreen(
                        recentlyPlayed = contentState.recentlyPlayed.take(maxItems),
                        popularSongs = contentState.popularSongs.take(maxItems),
                        isLoading = contentState.isLoading,
                        errorMessage = contentState.errorMessage,
                        onSongClick = onSongClick,
                        onRetry = onRetry,
                        onBrowseClick = { onSelectTab(CarScreen.Browse) },
                        currentlyPlayingMediaId = currentlyPlayingMediaId,
                        isPlaying = isPlaying,
                    )

                    CarScreen.Browse -> CarBrowseScreen(
                        genres = contentState.genres.take(maxItems),
                        onGenreClick = onGenreClick,
                        isLoading = contentState.isLoading,
                        errorMessage = contentState.errorMessage,
                        onRetry = onRetry,
                    )

                    CarScreen.Library -> when (val destination = drillDown) {
                        is CarDestination.Artist -> {
                            val artistLikedSongs = remember(
                                contentState.likedSongs,
                                destination.artistId,
                                maxItems,
                            ) {
                                contentState.likedSongs
                                    .filter { it.artistId == destination.artistId }
                                    .take(maxItems)
                            }
                            CarArtistLikedSongsScreen(
                                artistName = destination.artistName,
                                likedSongs = artistLikedSongs,
                                onBackClick = onBackFromDetail,
                                onSongClick = { song -> onArtistSongClick(artistLikedSongs, song) },
                                onShufflePlay = { onShuffleTracks(artistLikedSongs) },
                                currentlyPlayingMediaId = currentlyPlayingMediaId,
                                isPlaying = isPlaying,
                            )
                        }

                        is CarDestination.Album, is CarDestination.Playlist -> DetailRoute(
                            destination = destination,
                            detail = contentState.detail,
                            maxItems = maxItems,
                            onBackClick = onBackFromDetail,
                            onPlayTracks = onPlayTracks,
                            onShuffleTracks = onShuffleTracks,
                            onSongClick = onSongClick,
                            currentlyPlayingMediaId = currentlyPlayingMediaId,
                            isPlaying = isPlaying,
                            onRetry = onRetry,
                        )

                        null -> CarLibraryScreen(
                            recentlyPlayed = contentState.recentlyPlayed.take(maxItems),
                            playlists = contentState.playlists.take(maxItems),
                            albums = contentState.albums.take(maxItems),
                            favoriteArtists = contentState.favoriteArtists.take(maxItems),
                            likedSongCount = contentState.likedSongs.size,
                            onSongClick = onSongClick,
                            onPlaylistClick = onPlaylistClick,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            onFavouritesClick = { onSelectTab(CarScreen.Favourites) },
                            onBrowseClick = { onSelectTab(CarScreen.Browse) },
                            onSignOut = onSignOut,
                            userDisplayName = userDisplayName,
                            currentlyPlayingMediaId = currentlyPlayingMediaId,
                            isPlaying = isPlaying,
                            isLoading = contentState.isLoading,
                            errorMessage = contentState.errorMessage,
                            onRetry = onRetry,
                        )
                    }

                    CarScreen.Favourites -> CarFavouriteMusicScreen(
                        likedSongs = contentState.likedSongs.take(maxItems),
                        onSongClick = onLikedSongClick,
                        onBrowseClick = { onSelectTab(CarScreen.Browse) },
                        currentlyPlayingMediaId = currentlyPlayingMediaId,
                        isPlaying = isPlaying,
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
                onQueueClick = onQueueClick,
            )
        }
    }
}

/**
 * Applies the driving item cap to a loaded detail and picks the screen.
 *
 * `contentState.detail` is null for one frame after [drillDown] changes, before `openDetail`'s
 * first state write lands — and for one frame after *that* it can still hold the previous
 * destination. Both cases render the loading state rather than falling through, which is what
 * stops the wrong tracks appearing under the right title on entry.
 */
@Suppress("LongParameterList")
@Composable
private fun DetailRoute(
    destination: CarDestination,
    detail: CarDetailState?,
    maxItems: Int,
    onBackClick: () -> Unit,
    onPlayTracks: (List<Song>) -> Unit,
    onShuffleTracks: (List<Song>) -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    currentlyPlayingMediaId: String?,
    isPlaying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val capped = remember(detail, destination, maxItems) {
        detail?.takeIf { it.destination == destination }
            ?.let { loaded -> loaded.copy(tracks = loaded.tracks.take(maxItems)) }
            ?: CarDetailState(destination = destination, isLoading = true)
    }

    when (destination) {
        is CarDestination.Album -> CarAlbumScreen(
            detail = capped,
            onBackClick = onBackClick,
            onPlay = onPlayTracks,
            onShuffle = onShuffleTracks,
            onSongClick = onSongClick,
            modifier = modifier,
            currentlyPlayingMediaId = currentlyPlayingMediaId,
            isPlaying = isPlaying,
            onRetry = onRetry,
        )

        is CarDestination.Playlist -> CarPlaylistScreen(
            detail = capped,
            onBackClick = onBackClick,
            onPlay = onPlayTracks,
            onShuffle = onShuffleTracks,
            onSongClick = onSongClick,
            modifier = modifier,
            currentlyPlayingMediaId = currentlyPlayingMediaId,
            isPlaying = isPlaying,
            onRetry = onRetry,
        )

        // Routed by the caller's own branch; a when over a sealed interface must be exhaustive.
        is CarDestination.Artist -> Unit
    }
}
