package com.example.nyasaplayer.auto.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.auto.search.AutomotiveSearchResult
import com.example.nyasaplayer.auto.search.AutomotiveSearchResults
import com.example.nyasaplayer.auto.ui.components.CarAmbientBackground
import com.example.nyasaplayer.auto.ui.components.CarCardShape
import com.example.nyasaplayer.auto.ui.components.CarChip
import com.example.nyasaplayer.auto.ui.components.CarContentCard
import com.example.nyasaplayer.auto.ui.components.CarEmptyState
import com.example.nyasaplayer.auto.ui.components.CarErrorOverlay
import com.example.nyasaplayer.auto.ui.components.CarMiniPlayer
import com.example.nyasaplayer.auto.ui.components.CarNavRail
import com.example.nyasaplayer.auto.ui.components.CarPillButton
import com.example.nyasaplayer.auto.ui.components.CarRestrictionDialog
import com.example.nyasaplayer.auto.ui.components.CarSectionHeader
import com.example.nyasaplayer.auto.ui.components.CarSignOutConfirmation
import com.example.nyasaplayer.auto.ui.components.CarSystemBar
import com.example.nyasaplayer.auto.ui.components.CarTrackRow
import com.example.nyasaplayer.auto.ui.components.DriftDurationMs
import com.example.nyasaplayer.auto.ui.navigation.CarDestination
import com.example.nyasaplayer.auto.ui.navigation.CarScreen
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
import com.example.nyasaplayer.auto.ui.screens.CarProfileSwitcherScreen
import com.example.nyasaplayer.auto.ui.screens.CarQueueScreen
import com.example.nyasaplayer.auto.ui.screens.CarSearchResultsScreen
import com.example.nyasaplayer.auto.ui.screens.CarSearchScreen
import com.example.nyasaplayer.auto.ui.screens.CarSettingsScreen
import com.example.nyasaplayer.auto.ui.theme.CarMiniPlayerHeight
import com.example.nyasaplayer.auto.ui.theme.CarNavRailWidth
import com.example.nyasaplayer.auto.ui.theme.CarScreenMargin
import com.example.nyasaplayer.auto.ui.theme.CarSystemBarHeight
import com.example.nyasaplayer.auto.viewmodel.CarAuthUiState
import com.example.nyasaplayer.auto.viewmodel.CarDetailState
import com.example.nyasaplayer.auto.viewmodel.FavoriteArtist
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.OfflineBanner
import com.example.nyasaplayer.core.common.ui.theme.AppTheme
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.playback.PlaybackSnapshot
import com.example.nyasaplayer.core.playback.PlayerError
import com.example.nyasaplayer.core.playback.RepeatMode
import org.robolectric.Shadows.shadowOf

/**
 * Every launcher surface the PRD §12 exit measurement covers (criteria 2 and 3), each in the states
 * that change what is on screen.
 *
 * Rendered with state passed in, never through `AutomotiveApp`: its signed-in side needs three
 * Hilt ViewModels. Each case sits on the root background and the ambient layer the real shell
 * paints (frozen at its first frame, which is what a driver sees when motion is off), and a
 * tab screen sits in the slot the shell gives it — right of the rail, below the system bar,
 * above the mini-player, inside the screen margin — so it lays out at the width it really gets.
 *
 * [scope], when set, limits both measurements to what the case is about: the queue's remove
 * confirmation is the one thing the case opens, so the rows around it are left to the other queue
 * cases. (It renders inline inside its lazy item rather than as a modal over the queue.)
 *
 * [lowestDrift] runs the ambient layer's parked drift to the frame where its blue centre sits
 * lowest — furthest into the content region — instead of freezing it at the first frame.
 *
 * [scrollsList] measures the case again after every step of scrolling its outermost lazy list, so
 * the middle of a long list is measured as well as its ends. [onGlow] is false where the app paints
 * no ambient layer: `AuthGate` draws the auth screen on the bare root background. [family] names the
 * screen state a case belongs to, the same across its drift and scroll variants.
 */
internal class CarUiCase(
    val name: String,
    val scope: SemanticsMatcher? = null,
    val interact: (AndroidComposeTestRule<*, ComponentActivity>) -> Unit = {},
    val lowestDrift: Boolean = false,
    val scrollsList: Boolean = false,
    val onGlow: Boolean = true,
    val family: String = name,
    val content: @Composable () -> Unit,
)

private fun CarUiCase.copy(
    name: String = this.name,
    lowestDrift: Boolean = this.lowestDrift,
    scrollsList: Boolean = this.scrollsList,
) = CarUiCase(name, scope, interact, lowestDrift, scrollsList, onGlow, family, content)

private fun CarUiCase.scrolling() = copy(scrollsList = true)

/**
 * The shared head-unit canvas from robolectric.properties, at 2x rather than 1x: layout in dp is
 * unchanged, and a glyph gets enough pixels that its core renders at full colour for the contrast
 * measurement to recover.
 */
internal const val MeasurementQualifiers = "w1280dp-h800dp-xhdpi"

/**
 * Renders each case in turn and hands it to [measure] once it is idle.
 *
 * A fresh `ComposeView` per case, so nothing a case remembers — scroll position, an open dialog,
 * the view-scoped lazy-list prefetcher — carries into the next one.
 */
internal fun AndroidComposeTestRule<*, ComponentActivity>.forEachCarUiCase(
    cases: List<CarUiCase> = carUiCases,
    measure: (CarUiCase) -> Unit,
) {
    cases.forEach { case ->
        // The test clock runs an infinite animation only while it is advanced by hand.
        mainClock.autoAdvance = !case.lowestDrift
        runOnUiThread {
            activity.setContentView(ComposeView(activity).apply { setContent { CarUiFrame(case) } })
        }
        // One leg of the drift runs from the first frame to the far end, where the blue centre is lowest.
        if (case.lowestDrift) mainClock.advanceTimeBy(DriftDurationMs.toLong())
        case.interact(this)
        waitForIdle()
        measure(case)
        if (case.scrollsList) measureEachScrollStep(case, measure)
    }
    mainClock.autoAdvance = true
}

/**
 * Scrolls the case's outermost lazy list one item at a time and measures after every step.
 *
 * By index rather than `performScrollToNode`, which scrolls by deltas: under native graphics that
 * left Compose's `AndroidPrefetchScheduler` re-posting itself to the Choreographer indefinitely
 * (`View.drawingTime` stays 0 — Robolectric never draws through `ViewRootImpl`), so the looper never
 * went idle and the test hung. Jumping index by index does not.
 */
private fun AndroidComposeTestRule<*, ComponentActivity>.measureEachScrollStep(
    case: CarUiCase,
    measure: (CarUiCase) -> Unit,
) {
    val list = onAllNodes(hasScrollToIndexAction())[0]
    // The semantics publish no item count, and the action refuses an index past the end.
    var index = 1
    while (runCatching { list.performScrollToIndex(index) }.isSuccess) {
        waitForIdle()
        measure(case.copy(name = "${case.name}, scrolled to item $index", scrollsList = false))
        index++
    }
    check(index > 2) { "${case.name} scrolled no further than item ${index - 1}: is its list still lazy?" }
}

/** The root background, and the ambient layer on it where the app paints one. */
@Composable
private fun CarUiFrame(case: CarUiCase) {
    AppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NyasaBackground),
        ) {
            if (case.onGlow) CarAmbientBackground(animate = case.lowestDrift)
            case.content()
        }
    }
}

/**
 * The window's pixels, through the same `PixelCopy.request(Window, …)` call `captureToImage()` makes.
 *
 * Not `captureToImage()` itself: on ui-test 1.7.2 under Robolectric's paused looper it posts its
 * force-redraw to the main looper and then sleeps the test thread — which *is* the main looper —
 * waiting for it, so it always times out after 2s. The copy it would then make works; this makes it.
 */
internal fun AndroidComposeTestRule<*, ComponentActivity>.captureWindow(): Bitmap {
    val window = activity.window
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = -1
    PixelCopy.request(window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
    shadowOf(Looper.getMainLooper()).idle()
    check(result == PixelCopy.SUCCESS) { "PixelCopy failed with result $result" }
    return bitmap
}

/** Each case, then — where it sits on the glow — the same case at the drift's lowest frame, unscrolled. */
private fun List<CarUiCase>.withLowestDrift(): List<CarUiCase> = flatMap { case ->
    if (case.onGlow) {
        listOf(case, case.copy(name = "${case.name}, drift lowest", lowestDrift = true, scrollsList = false))
    } else {
        listOf(case)
    }
}

// ── Placement: where the shell puts each region ──

/** The content region `BrowseShell` gives a tab screen, with the mini-player showing. */
private val ContentSlot = Modifier
    .padding(start = CarNavRailWidth, top = CarSystemBarHeight, bottom = CarMiniPlayerHeight)
    .padding(CarScreenMargin)

@Composable
private fun InContentSlot(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = ContentSlot, content = content)
}

// ── Fixture data ──

private val Songs = listOf(
    Song(
        mediaId = "s1",
        title = "Purple Haze",
        artistId = "ar-jimi",
        artistName = "Jimi Hendrix",
        albumName = "Are You Experienced",
        durationMs = 170_000L,
    ),
    Song(mediaId = "s2", title = "Midnight Drive", artistId = "ar1", artistName = "The Weeknd", durationMs = 245_000L),
    Song(mediaId = "s3", title = "Thunder Road", artistName = "Bruce Springsteen", durationMs = 288_000L),
    Song(mediaId = "s4", title = "Blue in Green", artistName = "Miles Davis", durationMs = 337_000L),
    Song(mediaId = "s5", title = "Levitating", artistId = "ar2", artistName = "Dua Lipa", durationMs = 203_000L),
    Song(mediaId = "s6", title = "Blinding Lights", artistId = "ar1", artistName = "The Weeknd", durationMs = 200_000L),
)
private val NowPlaying = Songs[1]

private val Genres = listOf("Electronic", "Hip Hop", "Jazz", "Pop", "Rock", "Afrobeat", "Classical")
    .mapIndexed { index, name -> Genre(id = "g$index", name = name, songIds = listOf("s1", "s2")) }

private val Playlists = listOf(
    Playlist(id = "p1", name = "Road Trip", songIds = List(12) { "s$it" }),
    Playlist(id = "p2", name = "Late Night", songIds = List(8) { "s$it" }),
)

private val Albums = listOf(
    Album(id = "al1", name = "After Hours", artistName = "The Weeknd"),
    Album(id = "al2", name = "Future Nostalgia", artistName = "Dua Lipa"),
)

private val FavoriteArtists = listOf(
    FavoriteArtist(artistId = "ar1", artistName = "The Weeknd", coverUrl = "", likedCount = 2),
    FavoriteArtist(artistId = "ar2", artistName = "Dua Lipa", coverUrl = "", likedCount = 1),
)

private val PlayingSnapshot = PlaybackSnapshot(
    currentSong = NowPlaying,
    isPlaying = true,
    currentPositionMs = 94_000L,
    durationMs = NowPlaying.durationMs,
    hasPrevious = true,
    hasNext = true,
    repeatMode = RepeatMode.All,
    isShuffled = true,
    queueSize = Songs.size,
    queue = Songs,
    currentQueueIndex = 1,
    playWhenReady = true,
)
private val PausedSnapshot = PlayingSnapshot.copy(
    isPlaying = false,
    playWhenReady = false,
    repeatMode = RepeatMode.Off,
    isShuffled = false,
)
private val BufferingSnapshot = PlayingSnapshot.copy(isPlaying = false, isBuffering = true, repeatMode = RepeatMode.One)

private val SearchSongResults = AutomotiveSearchResults(
    query = "midnight",
    featured = AutomotiveSearchResult.SongResult(NowPlaying),
    songs = listOf(Songs[0], Songs[5]).map(AutomotiveSearchResult::SongResult),
    albums = listOf(AutomotiveSearchResult.AlbumResult(Albums[0])),
    artists = listOf(AutomotiveSearchResult.ArtistResult(Artist(id = "ar1", name = "The Weeknd", songCount = 12))),
    playlists = listOf(AutomotiveSearchResult.PlaylistResult(Playlists[1])),
)
private val SearchAlbumResults = AutomotiveSearchResults(
    query = "after hours",
    featured = AutomotiveSearchResult.AlbumResult(Albums[0]),
    songs = listOf(Songs[5]).map(AutomotiveSearchResult::SongResult),
)

private const val LoadError = "Couldn't reach Nyasa Music. Check your connection."
private const val DriverName = "Ada Lovelace"

// ── Cases ──

internal val carUiCases: List<CarUiCase> =
    chromeCases() + modalCases() + (componentCases() + authCases() + tabCases() + detailCases()).withLowestDrift() +
        playerCases() + queueCases() + sheetCases() + searchCases()

/** Only the content slot on the ambient layer, at the first and the lowest drift frame. */
internal val ambientCases: List<CarUiCase> = listOf(
    CarUiCase("ambient behind the content slot") {
        InContentSlot { Box(modifier = Modifier.fillMaxSize().testTag(ContentSlotTag)) }
    },
).withLowestDrift()

internal const val ContentSlotTag = "contentSlot"

private fun chromeCases(): List<CarUiCase> = listOf(
    CarUiCase("CarSystemBar") { CarSystemBar(onSearchClick = {}, onSettingsClick = {}, onAvatarClick = {}) },
    CarUiCase("OfflineBanner/offline") {
        OfflineBanner(isOffline = true, modifier = Modifier.padding(top = CarSystemBarHeight))
    },
) + CarScreen.entries.map { tab ->
    CarUiCase("CarNavRail/$tab selected") {
        CarNavRail(
            currentScreen = tab,
            onSelectTab = {},
            modifier = Modifier.padding(top = CarSystemBarHeight, bottom = CarMiniPlayerHeight),
        )
    }
} + listOf(
    CarUiCase("CarMiniPlayer/playing, liked") { AtBottom { MiniPlayer(PlayingSnapshot, isLiked = true) } },
    CarUiCase("CarMiniPlayer/paused, unliked") { AtBottom { MiniPlayer(PausedSnapshot, isLiked = false) } },
)

@Composable
private fun AtBottom(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter, content = content)
}

@Composable
private fun MiniPlayer(playback: PlaybackSnapshot, isLiked: Boolean) {
    CarMiniPlayer(
        playback = playback,
        onTogglePlayPause = {},
        onSkipNext = {},
        onSkipPrevious = {},
        onExpand = {},
        isLiked = isLiked,
    )
}

private fun modalCases(): List<CarUiCase> = listOf(
    errorCase(
        "CarErrorOverlay/playback error, retryable, skip next offered",
        PlayerError("Playback Error", "Source error: the stream stopped responding.", isRetryable = true),
        skipNext = true,
    ),
    errorCase(
        "CarErrorOverlay/playback error, retryable, no next track",
        PlayerError("Playback Error", "Source error: the stream stopped responding.", isRetryable = true),
        skipNext = false,
    ),
    errorCase(
        "CarErrorOverlay/playback error, not retryable",
        PlayerError("Nothing to Play", "This genre doesn't have any songs available yet."),
        skipNext = false,
    ),
    errorCase(
        "CarErrorOverlay/connection error, retryable",
        PlayerError(
            "No Connection",
            "Check your vehicle's internet connection",
            isPlaybackError = false,
            isRetryable = true,
        ),
        skipNext = false,
    ),
    errorCase(
        "CarErrorOverlay/connection error, not retryable",
        PlayerError("Player Error", "Could not connect to playback service", isPlaybackError = false),
        skipNext = false,
    ),
    CarUiCase("CarRestrictionDialog/entry refused") {
        CarRestrictionDialog(reason = "Settings are available when the vehicle is parked.", onDismiss = {})
    },
    CarUiCase("CarSignOutConfirmation") { CarSignOutConfirmation(onConfirm = {}, onDismiss = {}) },
)

private fun errorCase(name: String, error: PlayerError, skipNext: Boolean) = CarUiCase(name) {
    CarErrorOverlay(
        error = error,
        onDismiss = {},
        onRetry = {},
        onSkipNext = if (skipNext) ({}) else null,
    )
}

private fun componentCases(): List<CarUiCase> = listOf(
    CarUiCase("CarControls/chips and pills") {
        InContentSlot {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                CarSectionHeader(title = "Section header")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CarChip(label = "Selected chip", selected = true, onClick = {})
                    CarChip(label = "Unselected chip", selected = false, onClick = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CarPillButton(label = "Filled pill", onClick = {})
                    CarPillButton(label = "Ghost pill", onClick = {}, filled = false)
                }
            }
        }
    },
    CarUiCase("CarTrackRow/normal, current, liked, unliked") {
        InContentSlot {
            Column {
                TrackRow(Songs[0], isPlaying = false)
                TrackRow(NowPlaying, isPlaying = true)
                TrackRow(Songs[2], isPlaying = false, isLiked = true)
                TrackRow(Songs[3], isPlaying = false, isLiked = false)
            }
        }
    },
    CarUiCase("CarContentCard/normal, playing, circle, unavailable") {
        InContentSlot {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CarContentCard(title = "Road Trip", onClick = {}, subtitle = "12 songs")
                CarContentCard(title = "Midnight Drive", onClick = {}, subtitle = "The Weeknd", isPlaying = true)
                CarContentCard(title = "Dua Lipa", onClick = {}, subtitle = "1 liked", shape = CarCardShape.Circle)
                CarContentCard(title = "Downloads", onClick = {}, subtitle = "Coming soon", enabled = false)
            }
        }
    },
    CarUiCase("CarEmptyState/icon, no action") {
        InContentSlot {
            CarEmptyState(title = "Nothing here", body = "There is nothing to show yet.", icon = Icons.Filled.Search)
        }
    },
)

@Composable
private fun TrackRow(song: Song, isPlaying: Boolean, isLiked: Boolean? = null) {
    CarTrackRow(
        title = song.title,
        artist = song.artistName,
        duration = "3:45",
        isPlaying = isPlaying,
        onClick = {},
        onLikeToggle = if (isLiked != null) ({}) else null,
        isLiked = isLiked ?: true,
    )
}

private fun authCases(): List<CarUiCase> = listOf(
    "signed out" to CarAuthUiState(),
    "loading" to CarAuthUiState(isLoading = true),
    "error" to CarAuthUiState(errorMessage = "Google sign-in failed (code 7)"),
).map { (state, uiState) ->
    CarUiCase("CarAuthScreen/$state", onGlow = false) {
        CarAuthScreen(uiState = uiState, onGoogleToken = {}, onGoogleError = {})
    }
}

@Suppress("LongMethod")
private fun tabCases(): List<CarUiCase> = listOf(
    homeCase("loaded, playing", recent = Songs.take(3), popular = Songs.drop(3)).scrolling(),
    homeCase("loading", isLoading = true),
    homeCase("empty"),
    homeCase("error", error = LoadError),
    browseCase("loaded", genres = Genres).scrolling(),
    browseCase("loading", isLoading = true),
    browseCase("empty"),
    browseCase("error", error = LoadError),
    libraryCase("loaded, playing", loaded = true).scrolling(),
    libraryCase("loading", isLoading = true),
    libraryCase("empty"),
    libraryCase("error", error = LoadError),
    favouritesCase("loaded, playing, one pending unlike", songs = Songs.take(4)).scrolling(),
    favouritesCase("loading", isLoading = true),
    favouritesCase("empty (CarEmptyFavouritesScreen)"),
    favouritesCase("error", error = LoadError),
    CarUiCase("CarArtistLikedSongsScreen/loaded, playing, one pending unlike") {
        InContentSlot { ArtistLikedSongs(listOf(NowPlaying, Songs[5])) }
    },
    CarUiCase("CarArtistLikedSongsScreen/empty") { InContentSlot { ArtistLikedSongs(emptyList()) } },
)

private fun homeCase(
    state: String,
    recent: List<Song> = emptyList(),
    popular: List<Song> = emptyList(),
    isLoading: Boolean = false,
    error: String? = null,
) = CarUiCase("CarHomeScreen/$state") {
    InContentSlot {
        CarHomeScreen(
            recentlyPlayed = recent,
            popularSongs = popular,
            isLoading = isLoading,
            errorMessage = error,
            onSongClick = { _, _ -> },
            onRetry = {},
            onBrowseClick = {},
            currentlyPlayingMediaId = NowPlaying.mediaId,
            isPlaying = true,
        )
    }
}

private fun browseCase(
    state: String,
    genres: List<Genre> = emptyList(),
    isLoading: Boolean = false,
    error: String? = null,
) = CarUiCase("CarBrowseScreen/$state") {
    InContentSlot {
        CarBrowseScreen(
            genres = genres,
            onGenreClick = {},
            onLibraryClick = {},
            isLoading = isLoading,
            errorMessage = error,
        )
    }
}

private fun libraryCase(
    state: String,
    loaded: Boolean = false,
    isLoading: Boolean = false,
    error: String? = null,
) = CarUiCase("CarLibraryScreen/$state") {
    InContentSlot {
        CarLibraryScreen(
            recentlyPlayed = if (loaded) Songs.take(4) else emptyList(),
            playlists = if (loaded) Playlists else emptyList(),
            albums = if (loaded) Albums else emptyList(),
            favoriteArtists = if (loaded) FavoriteArtists else emptyList(),
            likedSongCount = if (loaded) 3 else 0,
            onSongClick = { _, _ -> },
            onPlaylistClick = {},
            onAlbumClick = {},
            onArtistClick = {},
            onFavouritesClick = {},
            onBrowseClick = {},
            currentlyPlayingMediaId = NowPlaying.mediaId,
            isPlaying = true,
            isLoading = isLoading,
            errorMessage = error,
        )
    }
}

private fun favouritesCase(
    state: String,
    songs: List<Song> = emptyList(),
    isLoading: Boolean = false,
    error: String? = null,
) = CarUiCase("CarFavouriteMusicScreen/$state") {
    InContentSlot {
        CarFavouriteMusicScreen(
            songs = songs,
            pendingUnlikes = setOf(Songs[2].mediaId),
            onSongClick = {},
            onPlayAll = {},
            onShuffle = {},
            onLikeToggle = {},
            onBrowseClick = {},
            currentlyPlayingMediaId = NowPlaying.mediaId,
            isPlaying = true,
            isLoading = isLoading,
            errorMessage = error,
        )
    }
}

@Composable
private fun ArtistLikedSongs(songs: List<Song>) {
    CarArtistLikedSongsScreen(
        artistName = "The Weeknd",
        artistCoverUrl = "",
        likedSongs = songs,
        pendingUnlikes = setOf(Songs[5].mediaId),
        onBackClick = {},
        onSongClick = {},
        onPlayAll = {},
        onShufflePlay = {},
        onLikeToggle = {},
        currentlyPlayingMediaId = NowPlaying.mediaId,
        isPlaying = true,
    )
}

/** Screens 10, 11 and 21 share one body but each carries its own empty copy, so all three run. */
private fun detailCases(): List<CarUiCase> {
    val screens = listOf(
        Triple("CarAlbumScreen", CarDestination.Album("al1"), "After Hours"),
        Triple("CarPlaylistScreen", CarDestination.Playlist("p2"), "Late Night"),
        Triple("CarArtistScreen", CarDestination.CatalogArtist("ar1"), "The Weeknd"),
    )
    return screens.flatMap { (screen, destination, title) ->
        val loaded = CarDetailState(
            destination = destination,
            title = title,
            subtitle = "The Weeknd",
            tracks = listOf(NowPlaying, Songs[5]),
            isLoading = false,
        )
        listOf(
            "loaded, playing" to loaded,
            "loading" to CarDetailState(destination = destination),
            "empty" to loaded.copy(tracks = emptyList()),
            "error" to loaded.copy(errorMessage = LoadError),
        ).map { (state, detail) ->
            CarUiCase("$screen/$state") { InContentSlot { Detail(screen, detail) } }
        }
    }
}

@Composable
private fun Detail(screen: String, detail: CarDetailState) {
    when (screen) {
        "CarAlbumScreen" -> CarAlbumScreen(
            detail = detail,
            onBackClick = {},
            onPlay = {},
            onShuffle = {},
            onSongClick = { _, _ -> },
            currentlyPlayingMediaId = NowPlaying.mediaId,
            isPlaying = true,
        )

        "CarPlaylistScreen" -> CarPlaylistScreen(
            detail = detail,
            onBackClick = {},
            onPlay = {},
            onShuffle = {},
            onSongClick = { _, _ -> },
            currentlyPlayingMediaId = NowPlaying.mediaId,
            isPlaying = true,
        )

        else -> CarArtistScreen(
            detail = detail,
            onBackClick = {},
            onPlay = {},
            onShuffle = {},
            onSongClick = { _, _ -> },
            currentlyPlayingMediaId = NowPlaying.mediaId,
            isPlaying = true,
        )
    }
}

private fun playerCases(): List<CarUiCase> = listOf(
    Triple("playing, liked, shuffle on, repeat all", PlayingSnapshot, true),
    Triple("paused, unliked, shuffle off, repeat off", PausedSnapshot, false),
    Triple("buffering, repeat one", BufferingSnapshot, true),
).map { (state, playback, liked) ->
    CarUiCase("CarFullPlayerScreen/$state") {
        CarFullPlayerScreen(
            playback = playback,
            onCollapseClick = {},
            onPlayPauseClick = {},
            onSkipNextClick = {},
            onSkipPreviousClick = {},
            onShuffleClick = {},
            onRepeatClick = {},
            onSeek = {},
            isLiked = liked,
        )
    }
}

private fun queueCases(): List<CarUiCase> {
    val removeConfirmDialog = hasClickAction() and hasAnyDescendant(hasText("Remove from queue?"))
    return listOf(
        queueCase("parked, playing", Songs, isDriving = false).scrolling(),
        queueCase("driving, locked, capped to 4", Songs, isDriving = true),
        queueCase("parked, one item (clear disabled)", listOf(NowPlaying), isDriving = false, currentIndex = 0),
        queueCase("parked, empty", emptyList(), isDriving = false, currentIndex = -1),
        CarUiCase(
            name = "CarQueueScreen/parked, remove confirmation open",
            scope = removeConfirmDialog or hasAnyAncestor(removeConfirmDialog),
            interact = { rule -> rule.onAllNodesWithContentDescription("Remove from queue")[0].performClick() },
        ) { Queue(Songs, isDriving = false, currentIndex = 1) },
    )
}

private fun queueCase(state: String, queue: List<Song>, isDriving: Boolean, currentIndex: Int = 1) =
    CarUiCase("CarQueueScreen/$state") { Queue(queue, isDriving, currentIndex) }

@Composable
private fun Queue(queue: List<Song>, isDriving: Boolean, currentIndex: Int) {
    CarQueueScreen(
        queue = queue,
        currentIndex = currentIndex,
        isDriving = isDriving,
        maxItems = 4,
        onCloseClick = {},
        onSkipTo = {},
        onRemove = {},
        onClearQueue = {},
        isPlaying = true,
    )
}

private fun sheetCases(): List<CarUiCase> = listOf(
    CarUiCase("CarSettingsScreen/account") {
        CarSettingsScreen(displayName = DriverName, appVersion = "1.0", onSignOut = {}, onClose = {})
    },
    CarUiCase("CarSettingsScreen/no display name") {
        CarSettingsScreen(displayName = "", appVersion = "1.0", onSignOut = {}, onClose = {})
    },
    CarUiCase("CarProfileSwitcherScreen/account") {
        CarProfileSwitcherScreen(displayName = DriverName, onSignOut = {}, onClose = {})
    },
    CarUiCase("CarProfileSwitcherScreen/no display name") {
        CarProfileSwitcherScreen(displayName = "", onSignOut = {}, onClose = {})
    },
)

private fun searchCases(): List<CarUiCase> = listOf(
    searchCase("parked, idle, recent searches", query = "", recent = listOf("weeknd", "jazz"), canType = true),
    searchCase("parked, idle, no recent searches", query = "", recent = emptyList(), canType = true),
    searchCase("parked, query typed", query = "midnight", recent = listOf("weeknd"), canType = true),
    searchCase("driving, typing refused (voice prompt)", query = "", recent = listOf("weeknd"), canType = false),
    resultsCase("song top result, playing", SearchSongResults).scrolling(),
    resultsCase("album top result", SearchAlbumResults),
    resultsCase("loading", AutomotiveSearchResults(), isLoading = true),
    resultsCase("no results", AutomotiveSearchResults()),
    resultsCase("error", AutomotiveSearchResults(), error = "Search is unavailable right now."),
)

private fun searchCase(state: String, query: String, recent: List<String>, canType: Boolean) =
    CarUiCase("CarSearchScreen/$state") {
        CarSearchScreen(
            query = query,
            recentQueries = recent,
            canType = canType,
            onQueryChange = {},
            onSubmit = {},
            onClearQuery = {},
            onEditingChange = {},
            onRecentClick = {},
            onBrowseGenres = {},
            onBrowseLibrary = {},
            onClose = {},
        )
    }

private fun resultsCase(
    state: String,
    results: AutomotiveSearchResults,
    isLoading: Boolean = false,
    error: String? = null,
) = CarUiCase("CarSearchResultsScreen/$state") {
    CarSearchResultsScreen(
        query = results.query.ifEmpty { "zzzz" },
        results = results,
        isLoading = isLoading,
        errorMessage = error,
        onBackToSearch = {},
        onClear = {},
        onRetry = {},
        onResultClick = {},
        currentlyPlayingMediaId = NowPlaying.mediaId,
        isPlaying = true,
    )
}
