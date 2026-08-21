package com.example.nyasaplayer.auto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
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
import com.example.nyasaplayer.auto.ui.navigation.CarSheet
import com.example.nyasaplayer.auto.ui.navigation.CarUiLocation
import com.example.nyasaplayer.auto.ui.navigation.GateResult
import com.example.nyasaplayer.auto.ui.navigation.gate
import com.example.nyasaplayer.auto.ui.screens.CarAlbumScreen
import com.example.nyasaplayer.auto.ui.screens.CarArtistLikedSongsScreen
import com.example.nyasaplayer.auto.ui.screens.CarArtistScreen
import com.example.nyasaplayer.auto.ui.screens.CarAuthScreen
import com.example.nyasaplayer.auto.ui.screens.CarBrowseScreen
import com.example.nyasaplayer.auto.ui.screens.CarFavouriteMusicScreen
import com.example.nyasaplayer.auto.ui.screens.CarFullPlayerScreen
import com.example.nyasaplayer.auto.ui.screens.CarHomeScreen
import com.example.nyasaplayer.auto.ui.screens.CarLibraryScreen
import com.example.nyasaplayer.auto.ui.screens.CarPlaylistScreen
import com.example.nyasaplayer.auto.ui.screens.CarQueueScreen
import com.example.nyasaplayer.auto.ui.screens.CarSearchResultsScreen
import com.example.nyasaplayer.auto.ui.screens.CarSearchScreen
import com.example.nyasaplayer.auto.ui.screens.artistLikedSongs
import com.example.nyasaplayer.auto.ui.theme.CarScreenMargin
import com.example.nyasaplayer.auto.viewmodel.AutomotiveAuthViewModel
import com.example.nyasaplayer.auto.viewmodel.AutomotiveContentState
import com.example.nyasaplayer.auto.viewmodel.AutomotiveContentViewModel
import com.example.nyasaplayer.auto.viewmodel.AutomotivePlayerViewModel
import com.example.nyasaplayer.auto.viewmodel.AutomotiveSearchUiState
import com.example.nyasaplayer.auto.viewmodel.AutomotiveSearchViewModel
import com.example.nyasaplayer.auto.viewmodel.AutomotiveUiState
import com.example.nyasaplayer.auto.viewmodel.CarDetailState
import com.example.nyasaplayer.auto.viewmodel.FavoriteArtist
import com.example.nyasaplayer.auto.viewmodel.UxRestrictionState
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
    searchViewModel: AutomotiveSearchViewModel = hiltViewModel(),
) {
    val playerState by playerViewModel.uiState.collectAsState()
    val contentState by contentViewModel.contentState.collectAsState()
    val searchState by searchViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        contentViewModel.reloadUserContent()
    }

    var currentScreen by rememberSaveable { mutableStateOf(CarScreen.Home) }
    var drillDown by rememberSaveable { mutableStateOf<CarDestination?>(null) }
    // The queue sits *above* the full player rather than replacing it, so closing the queue has
    // to reveal the player again. A single nullable value cannot express that.
    var overlays by rememberSaveable(stateSaver = OverlayStackSaver) {
        mutableStateOf(emptyList<CarOverlay>())
    }
    var sheet by rememberSaveable { mutableStateOf<CarSheet?>(null) }
    var denialReason by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val openFullPlayer: () -> Unit = {
        sheet = null
        overlays = overlaysWith(overlays, CarOverlay.FullPlayer)
    }
    val openQueue: () -> Unit = {
        sheet = null
        overlays = overlaysWith(overlays, CarOverlay.Queue)
    }
    val closeTopOverlay: () -> Unit = { overlays = overlays.dropLast(1) }
    val clearTransientSurfaces: () -> Unit = {
        overlays = emptyList()
        sheet = null
    }

    // The platform's own NO_KEYBOARD answer, not a driving guess (spec D31).
    val canType = !playerState.restrictions.noTextEntry

    // One tab-selection path for the rail and for the search sheet's browse-by shortcuts, so
    // "leaving for a tab" always means the same thing.
    val selectTab = { screen: CarScreen ->
        drillDown = null
        clearTransientSurfaces()
        currentScreen = screen
    }

    // The results the driver can see, which is also the list a tap plays. Songs only until
    // Task 5 renders the album, artist and playlist sections.
    val visibleResults = rememberVisible(searchState.results.songQueue, playerState.restrictions)

    val location = carUiLocation(
        tab = currentScreen,
        overlay = overlays.lastOrNull(),
        drillDown = drillDown,
        sheet = sheet,
        searchTextEntryActive = searchState.isEditing,
    )

    // Keyed on both the restrictions and the location so it fires when the vehicle starts
    // moving *and* when the user navigates. Gating entry alone is not enough — a vehicle
    // can start moving while the driver is already inside a restricted screen.
    LaunchedEffect(playerState.restrictions, location) {
        when (val result = gate(location, playerState.restrictions)) {
            is GateResult.Allowed -> Unit
            is GateResult.Denied -> {
                // evictTo carries a whole CarUiLocation, but only its tab is usable — see the
                // lossy-projection note on CarUiLocation.tabRoot().
                clearTransientSurfaces()
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

    // Symmetric with the detail effect. openFavourites() does not freeze — the freeze happens at
    // the first unlike (spec D19) — but closeFavourites() must run on leaving, or the next visit
    // inherits the previous visit's held-back rows.
    LaunchedEffect(currentScreen) {
        if (currentScreen == CarScreen.Favourites) {
            contentViewModel.openFavourites()
        } else {
            contentViewModel.closeFavourites()
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

        if (CarOverlay.FullPlayer in overlays) {
            CarFullPlayerScreen(
                playback = playerState.playback,
                onCollapseClick = closeTopOverlay,
                onPlayPauseClick = playerViewModel::togglePlayPause,
                onSkipNextClick = playerViewModel::skipNext,
                onSkipPreviousClick = playerViewModel::skipPrevious,
                onShuffleClick = playerViewModel::toggleShuffle,
                onRepeatClick = playerViewModel::toggleRepeatMode,
                onSeek = playerViewModel::seekTo,
                isLiked = playerState.isCurrentSongLiked,
                onLikeClick = playerViewModel::toggleLike,
                onQueueClick = openQueue,
            )
        } else {
            BrowseShell(
                currentScreen = currentScreen,
                playerState = playerState,
                contentState = contentState,
                onSignOut = onSignOut,
                userDisplayName = userDisplayName,
                // The search control opens the search view, not whatever results were left
                // over from an earlier trip. The draft query survives, so re-running it is one
                // press away.
                onSearchClick = {
                    searchViewModel.backToSearch()
                    clearTransientSurfaces()
                    sheet = CarSheet.Search
                },
                onSelectTab = selectTab,
                onExpandPlayer = openFullPlayer,
                onQueueClick = openQueue,
                decorativeMotionEnabled = motionEnabled,
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onSkipNext = playerViewModel::skipNext,
                onSkipPrevious = playerViewModel::skipPrevious,
                onSongClick = { songs, song ->
                    playerViewModel.playSong(songs, song)
                    openFullPlayer()
                },
                onRetry = contentViewModel::retryLoad,
                onRetryDetail = { drillDown?.let(contentViewModel::openDetail) },
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
                    openFullPlayer()
                },
                onShuffleTracks = { songs ->
                    playerViewModel.shufflePlay(songs)
                    openFullPlayer()
                },
                onPlayTracks = { tracks ->
                    tracks.firstOrNull()?.let { first ->
                        playerViewModel.playSong(tracks, first)
                        openFullPlayer()
                    }
                },
                onGenreClick = { genre ->
                    scope.launch {
                        val songs = contentViewModel.getSongsByGenre(genre.id)
                        if (songs.isNotEmpty()) {
                            playerViewModel.shufflePlay(songs)
                            openFullPlayer()
                        } else {
                            playerViewModel.reportEmptyGenrePlayback()
                        }
                    }
                },
                onLikeClick = playerViewModel::toggleLike,
                onLikeToggle = { song, freeze ->
                    scope.launch {
                        if (!contentViewModel.toggleFavourite(song.mediaId, freeze = freeze)) {
                            playerViewModel.reportUnlikeFailed()
                        }
                    }
                },
            )
        }

        if (sheet == CarSheet.Search) {
            SearchSheet(
                state = searchState,
                canType = canType,
                visibleResults = visibleResults,
                currentlyPlayingMediaId = playerState.playback.currentSong?.mediaId,
                isPlaying = playerState.playback.isPlaying,
                onQueryChange = searchViewModel::onQueryChange,
                onSubmit = searchViewModel::submitSearch,
                onClearQuery = searchViewModel::clearQuery,
                onEditingChange = searchViewModel::setEditing,
                onRecentClick = searchViewModel::selectRecentQuery,
                onBackToSearch = searchViewModel::backToSearch,
                onRetry = searchViewModel::retrySearch,
                onBrowseGenres = { selectTab(CarScreen.Browse) },
                onBrowseLibrary = { selectTab(CarScreen.Library) },
                // The editing flag clears itself when the field leaves composition
                // (see CarSearchScreen), so dropping the sheet is all this has to do.
                onClose = { sheet = null },
                onPlay = { songs, song ->
                    playerViewModel.playSong(songs, song)
                    openFullPlayer()
                },
            )
        }

        if (CarOverlay.Queue in overlays) {
            CarQueueScreen(
                queue = playerState.playback.queue,
                currentIndex = playerState.playback.currentQueueIndex,
                isPlaying = playerState.playback.isPlaying,
                isDriving = playerState.restrictions.isDistractionOptimized,
                maxItems = playerState.restrictions.maxCumulativeContentItems,
                onCloseClick = closeTopOverlay,
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
 * Collapses the scattered pieces of navigation state into the one value [gate] decides on.
 * Derived, not authoritative — the individual values stay where the screens read them.
 *
 * Internal rather than private so CarUiLocationTest can exercise the mapping directly.
 */
internal fun carUiLocation(
    tab: CarScreen,
    overlay: CarOverlay?,
    drillDown: CarDestination?,
    sheet: CarSheet?,
    searchTextEntryActive: Boolean,
): CarUiLocation = CarUiLocation(
    tab = tab,
    overlay = overlay,
    // Every destination is one step from a tab root (D8). There is no depth 2 in A3.
    drillDepth = if (drillDown != null) 1 else 0,
    // Settings and Profile are A7. The field exists so that slice has nothing to retrofit.
    sheet = sheet,
    // Open-but-idle search is browsing, not typing, and stays allowed while driving. Only an
    // active editable field is text entry, which is why this is the ViewModel's explicit flag
    // and not "the query is non-empty" — a query left over from a parked search is not typing.
    textEntryActive = sheet == CarSheet.Search && searchTextEntryActive,
)

/**
 * The slice of [items] the driver may see, memoized.
 *
 * The `remember` is not decoration: `AutomotiveUiState` is unstable — `PlaybackSnapshot.queue` is
 * a `List` — so the 500ms position poller hands [BrowseShell] a new identity twice a second while
 * music plays. Deriving these lists in the body would allocate a fresh one per tick, and the
 * children compare `List` params by identity, so every tab would recompose in full at 2 Hz.
 */
@Composable
private fun <T> rememberVisible(items: List<T>, restrictions: UxRestrictionState): List<T> =
    remember(items, restrictions) { restrictions.cap(items) }

/**
 * The overlay stack after opening [opening].
 *
 * The full player replaces whatever was there; the queue pushes onto it, because the queue is
 * shown *above* the player and closing it must reveal the player again. Pure so the push and pop
 * rules are testable without rendering the shell.
 */
internal fun overlaysWith(current: List<CarOverlay>, opening: CarOverlay): List<CarOverlay> =
    when (opening) {
        CarOverlay.FullPlayer -> listOf(CarOverlay.FullPlayer)
        CarOverlay.Queue -> if (current.lastOrNull() == opening) current else current + opening
    }

/**
 * Enum names, and a restore that tolerates names this build no longer has.
 *
 * Not needed to store the list: Compose's `canBeSavedToBundle` accepts anything `Serializable`,
 * which every list produced here is. It is needed for the restore side. Saved instance state can
 * outlive an app update, so a removed or renamed `CarOverlay` would otherwise throw out of
 * `valueOf` while the Activity is being restored — a crash on launch, for a transient surface not
 * worth one. Landing
 * on the tab root is the right answer for state we cannot honour. The plain `rememberSaveable`
 * enums here (`currentScreen`, `sheet`) carry the same risk through the autoSaver and are not
 * hardened; this is simply the first place it is explicit enough to handle.
 */
private val OverlayStackSaver = listSaver<List<CarOverlay>, String>(
    save = { it.map(CarOverlay::name) },
    restore = { saved -> runCatching { saved.map(CarOverlay::valueOf) }.getOrDefault(emptyList()) },
)

/**
 * The search sheet's two views.
 *
 * A committed query is what "showing results" means. Deriving the view from it beats a second
 * boolean that can disagree with the ViewModel about which one is open — Back is `backToSearch()`
 * dropping the submitted query, not a flag flip the state knows nothing about.
 */
@Suppress("LongParameterList")
@Composable
private fun SearchSheet(
    state: AutomotiveSearchUiState,
    canType: Boolean,
    visibleResults: List<Song>,
    currentlyPlayingMediaId: String?,
    isPlaying: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearQuery: () -> Unit,
    onEditingChange: (Boolean) -> Unit,
    onRecentClick: (String) -> Unit,
    onBackToSearch: () -> Unit,
    onRetry: () -> Unit,
    onBrowseGenres: () -> Unit,
    onBrowseLibrary: () -> Unit,
    onClose: () -> Unit,
    onPlay: (List<Song>, Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.submittedQuery.isNotEmpty()) {
        CarSearchResultsScreen(
            query = state.submittedQuery,
            results = visibleResults,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            onBackToSearch = onBackToSearch,
            onClear = onClearQuery,
            onRetry = onRetry,
            onSongClick = onPlay,
            modifier = modifier,
            currentlyPlayingMediaId = currentlyPlayingMediaId,
            isPlaying = isPlaying,
        )
    } else {
        CarSearchScreen(
            query = state.query,
            recentQueries = state.recentQueries,
            canType = canType,
            onQueryChange = onQueryChange,
            onSubmit = onSubmit,
            onClearQuery = onClearQuery,
            onEditingChange = onEditingChange,
            onRecentClick = onRecentClick,
            onBrowseGenres = onBrowseGenres,
            onBrowseLibrary = onBrowseLibrary,
            onClose = onClose,
            modifier = modifier,
        )
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
    onSearchClick: () -> Unit,
    onSelectTab: (CarScreen) -> Unit,
    onExpandPlayer: () -> Unit,
    onQueueClick: () -> Unit,
    decorativeMotionEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    onRetry: () -> Unit,
    onRetryDetail: () -> Unit,
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
    onLikeToggle: (Song, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentlyPlayingMediaId = playerState.playback.currentSong?.mediaId
    val isPlaying = playerState.playback.isPlaying
    val restrictions = playerState.restrictions

    Column(modifier = modifier.fillMaxSize()) {
        // Settings and profile are still no-ops and render disabled until A7.
        CarSystemBar(
            onSearchClick = onSearchClick,
            onSettingsClick = {},
            onAvatarClick = {},
        )

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
                        recentlyPlayed = rememberVisible(contentState.recentlyPlayed, restrictions),
                        popularSongs = rememberVisible(contentState.popularSongs, restrictions),
                        isLoading = contentState.isLoading,
                        errorMessage = contentState.errorMessage,
                        onSongClick = onSongClick,
                        onRetry = onRetry,
                        onBrowseClick = { onSelectTab(CarScreen.Browse) },
                        currentlyPlayingMediaId = currentlyPlayingMediaId,
                        isPlaying = isPlaying,
                    )

                    CarScreen.Browse -> CarBrowseScreen(
                        genres = rememberVisible(contentState.genres, restrictions),
                        onGenreClick = onGenreClick,
                        onLibraryClick = { onSelectTab(CarScreen.Library) },
                        isLoading = contentState.isLoading,
                        errorMessage = contentState.errorMessage,
                        onRetry = onRetry,
                    )

                    CarScreen.Library -> when (val destination = drillDown) {
                        is CarDestination.Artist -> {
                            val artistSongs = remember(
                                contentState.likedSongs,
                                destination.artistId,
                                restrictions,
                            ) {
                                artistLikedSongs(
                                    contentState.likedSongs,
                                    destination.artistId,
                                    restrictions,
                                )
                            }
                            val artistCoverUrl = remember(
                                contentState.favoriteArtists,
                                destination.artistId,
                            ) {
                                contentState.favoriteArtists
                                    .firstOrNull { it.artistId == destination.artistId }
                                    ?.coverUrl
                                    .orEmpty()
                            }
                            CarArtistLikedSongsScreen(
                                artistName = destination.artistName,
                                artistCoverUrl = artistCoverUrl,
                                likedSongs = artistSongs,
                                pendingUnlikes = contentState.pendingUnlikes,
                                onBackClick = onBackFromDetail,
                                onSongClick = { song -> onArtistSongClick(artistSongs, song) },
                                onPlayAll = { onPlayTracks(artistSongs) },
                                onShufflePlay = { onShuffleTracks(artistSongs) },
                                // Live list: the row leaves on the next emission (D25).
                                onLikeToggle = likeToggle(onLikeToggle, freeze = false),
                                currentlyPlayingMediaId = currentlyPlayingMediaId,
                                isPlaying = isPlaying,
                            )
                        }

                        is CarDestination.Album,
                        is CarDestination.Playlist,
                        is CarDestination.CatalogArtist,
                        -> DetailRoute(
                            destination = destination,
                            detail = contentState.detail,
                            restrictions = restrictions,
                            onBackClick = onBackFromDetail,
                            onPlayTracks = onPlayTracks,
                            onShuffleTracks = onShuffleTracks,
                            onSongClick = onSongClick,
                            currentlyPlayingMediaId = currentlyPlayingMediaId,
                            isPlaying = isPlaying,
                            onRetry = onRetryDetail,
                        )

                        null -> CarLibraryScreen(
                            recentlyPlayed = rememberVisible(contentState.recentlyPlayed, restrictions),
                            playlists = rememberVisible(contentState.playlists, restrictions),
                            albums = rememberVisible(contentState.albums, restrictions),
                            favoriteArtists = rememberVisible(
                                contentState.favoriteArtists,
                                restrictions,
                            ),
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

                    CarScreen.Favourites -> CarFavouritesRoute(
                        contentState = contentState,
                        restrictions = restrictions,
                        onSongClick = onSongClick,
                        onPlayTracks = onPlayTracks,
                        onShuffleTracks = onShuffleTracks,
                        onLikeToggle = onLikeToggle,
                        onBrowseClick = { onSelectTab(CarScreen.Browse) },
                        onRetry = onRetry,
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
 * Screen 8's binding to [AutomotiveContentState], lifted out of [BrowseShell] so a Compose test
 * can render it without Hilt or a car.
 *
 * Which fields it reads *is* the behaviour: pointing [CarFavouriteMusicScreen] at the catalogue's
 * `isLoading`/`errorMessage` instead of the favourites pair puts a genres failure over screen 17
 * (A4 review row #8). CarFavouritesRouteTest fails when that happens.
 */
@Suppress("LongParameterList")
@Composable
internal fun CarFavouritesRoute(
    contentState: AutomotiveContentState,
    restrictions: UxRestrictionState,
    onSongClick: (List<Song>, Song) -> Unit,
    onPlayTracks: (List<Song>) -> Unit,
    onShuffleTracks: (List<Song>) -> Unit,
    onLikeToggle: (Song, Boolean) -> Unit,
    onBrowseClick: () -> Unit,
    onRetry: () -> Unit,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
) {
    val favouriteSongs = rememberVisible(
        contentState.favourites ?: contentState.likedSongs,
        restrictions,
    )
    CarFavouriteMusicScreen(
        songs = favouriteSongs,
        pendingUnlikes = contentState.pendingUnlikes,
        onSongClick = { song -> onSongClick(favouriteSongs, song) },
        onPlayAll = { onPlayTracks(favouriteSongs) },
        onShuffle = { onShuffleTracks(favouriteSongs) },
        // Frozen list: the row holds its place until the visit ends (D19).
        onLikeToggle = likeToggle(onLikeToggle, freeze = true),
        onBrowseClick = onBrowseClick,
        currentlyPlayingMediaId = currentlyPlayingMediaId,
        isPlaying = isPlaying,
        // Not contentState.isLoading/errorMessage: those belong to the catalogue. Screen 8
        // reads its own pair.
        isLoading = contentState.favouritesLoading,
        errorMessage = contentState.favouritesError,
        onRetry = onRetry,
    )
}

/**
 * Binds one screen's freeze contract onto the shared like callback.
 *
 * The flag is the difference between Favourites (freeze, spec D19) and the artist drill-down
 * (live, spec D25), and swapping the two reverts the slice while every unit test stays green —
 * they all call the ViewModel directly. It is a named argument at both call sites for that
 * reason; a Kotlin function type cannot take one at its own invocation.
 */
private fun likeToggle(onLikeToggle: (Song, Boolean) -> Unit, freeze: Boolean): (Song) -> Unit =
    { song -> onLikeToggle(song, freeze) }

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
    restrictions: UxRestrictionState,
    onBackClick: () -> Unit,
    onPlayTracks: (List<Song>) -> Unit,
    onShuffleTracks: (List<Song>) -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    currentlyPlayingMediaId: String?,
    isPlaying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val capped = remember(detail, destination, restrictions) {
        detail?.takeIf { it.destination == destination }
            ?.let { loaded -> loaded.copy(tracks = restrictions.cap(loaded.tracks)) }
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

        is CarDestination.CatalogArtist -> CarArtistScreen(
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

        // The liked-songs artist screen, routed by the caller's own branch; a when over a sealed
        // interface must be exhaustive.
        is CarDestination.Artist -> Unit
    }
}
