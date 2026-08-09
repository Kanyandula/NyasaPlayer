// @Preview composables are invoked by Android Studio tooling, not directly — suppress false positives.
@file:Suppress("UnusedPrivateMember")

package com.example.nyasaplayer.auto.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.example.nyasaplayer.auto.ui.components.CarErrorOverlay
import com.example.nyasaplayer.auto.ui.components.CarMiniPlayer
import com.example.nyasaplayer.auto.ui.screens.CarArtistLikedSongsScreen
import com.example.nyasaplayer.auto.ui.screens.CarBrowseScreen
import com.example.nyasaplayer.auto.ui.screens.CarFullPlayerScreen
import com.example.nyasaplayer.auto.ui.screens.CarHomeScreen
import com.example.nyasaplayer.auto.ui.screens.CarLibraryScreen
import com.example.nyasaplayer.auto.viewmodel.FavoriteArtist
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.theme.AppTheme
import com.example.nyasaplayer.core.playback.PlaybackSnapshot
import com.example.nyasaplayer.core.playback.PlayerError
import com.example.nyasaplayer.core.playback.RepeatMode

// ── Preview Data ──

private val PreviewSong = Song(
    mediaId = "1",
    title = "Purple Haze",
    artistName = "Jimi Hendrix",
    albumName = "Are You Experienced",
    durationMs = 342_000L,
    imageUrl = "",
)

private val PreviewSongs = listOf(
    PreviewSong,
    Song(mediaId = "2", title = "Midnight Drive", artistName = "The Weeknd", durationMs = 245_000L),
    Song(mediaId = "3", title = "Thunder Road", artistName = "Bruce Springsteen", durationMs = 298_000L),
    Song(mediaId = "4", title = "Blue Notes", artistName = "Miles Davis", durationMs = 412_000L),
)

private const val PreviewPositionMs = 154_000L

private val PreviewPlayback = PlaybackSnapshot(
    currentSong = PreviewSong,
    isPlaying = true,
    currentPositionMs = PreviewPositionMs,
    durationMs = PreviewSong.durationMs,
    hasPrevious = true,
    hasNext = true,
    repeatMode = RepeatMode.Off,
)

private val PreviewAlbums = listOf(
    Album(id = "a1", name = "Electronic Vibes", artistName = "Various", songIds = List(50) { "s$it" }),
    Album(id = "a2", name = "Hip Hop Essentials", artistName = "Various", songIds = List(42) { "s$it" }),
    Album(id = "a3", name = "Jazz Classics", artistName = "Various", songIds = List(38) { "s$it" }),
    Album(id = "a4", name = "Pop Hits", artistName = "Various", songIds = List(65) { "s$it" }),
)

private val PreviewGenres = listOf(
    Genre(id = "g1", name = "Electronic", imageUrl = ""),
    Genre(id = "g2", name = "Hip Hop", imageUrl = ""),
    Genre(id = "g3", name = "Jazz", imageUrl = ""),
    Genre(id = "g4", name = "Pop", imageUrl = ""),
)

private val PreviewFavoriteArtists = listOf(
    FavoriteArtist(artistId = "ar1", artistName = "The Weeknd", coverUrl = "", likedCount = 5),
    FavoriteArtist(artistId = "ar2", artistName = "Dua Lipa", coverUrl = "", likedCount = 3),
    FavoriteArtist(artistId = "ar3", artistName = "Bruno Mars", coverUrl = "", likedCount = 2),
    FavoriteArtist(artistId = "ar4", artistName = "Adele", coverUrl = "", likedCount = 1),
)

// ── Preview Composables ──

@Preview(
    name = "Home Screen",
    device = Devices.AUTOMOTIVE_1024p,
    showBackground = true,
    widthDp = 1280,
    heightDp = 720,
)
@Composable
private fun HomeScreenPreview() {
    AppTheme {
        CarHomeScreen(
            recentlyPlayed = PreviewSongs,
            popularSongs = PreviewSongs,
            isLoading = false,
            errorMessage = null,
            onSongClick = { _, _ -> },
            onRetry = {},
            onBrowseClick = {},
            currentlyPlayingMediaId = "1",
            isPlaying = true,
        )
    }
}

@Preview(
    name = "Full Player",
    device = Devices.AUTOMOTIVE_1024p,
    showBackground = true,
    widthDp = 1280,
    heightDp = 720,
)
@Composable
private fun FullPlayerPreview() {
    AppTheme {
        CarFullPlayerScreen(
            playback = PreviewPlayback,
            onCollapseClick = {},
            onPlayPauseClick = {},
            onSkipNextClick = {},
            onSkipPreviousClick = {},
            onShuffleClick = {},
            onRepeatClick = {},
            onSeek = {},
            isLiked = true,
            onLikeClick = {},
        )
    }
}

@Preview(
    name = "Browse Screen",
    device = Devices.AUTOMOTIVE_1024p,
    showBackground = true,
    widthDp = 1280,
    heightDp = 720,
)
@Composable
private fun BrowseScreenPreview() {
    AppTheme {
        CarBrowseScreen(
            genres = PreviewGenres,
            onGenreClick = {},
        )
    }
}

@Preview(
    name = "Library Screen",
    device = Devices.AUTOMOTIVE_1024p,
    showBackground = true,
    widthDp = 1280,
    heightDp = 720,
)
@Composable
private fun LibraryScreenPreview() {
    AppTheme {
        CarLibraryScreen(
            favoriteArtists = PreviewFavoriteArtists,
            albums = PreviewAlbums,
            onArtistClick = {},
            onAlbumClick = {},
            likedSongs = PreviewSongs,
            currentlyPlayingMediaId = "1",
            isPlaying = true,
            onShuffleLikedSongs = {},
            onLikedSongClick = {},
            onSignOut = {},
            userDisplayName = "John Doe",
        )
    }
}

@Preview(
    name = "Artist Liked Songs",
    device = Devices.AUTOMOTIVE_1024p,
    showBackground = true,
    widthDp = 1280,
    heightDp = 720,
)
@Composable
private fun ArtistLikedSongsPreview() {
    AppTheme {
        CarArtistLikedSongsScreen(
            artistName = "The Weeknd",
            likedSongs = PreviewSongs,
            onBackClick = {},
            onSongClick = {},
            onShufflePlay = {},
            currentlyPlayingMediaId = "1",
            isPlaying = true,
        )
    }
}

@Preview(
    name = "Mini Player",
    showBackground = true,
    widthDp = 1280,
    heightDp = 112,
)
@Composable
private fun MiniPlayerPreview() {
    AppTheme {
        CarMiniPlayer(
            playback = PreviewPlayback,
            onTogglePlayPause = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onExpand = {},
            isLiked = true,
            onLikeClick = {},
        )
    }
}

@Preview(
    name = "Error Overlay",
    device = Devices.AUTOMOTIVE_1024p,
    showBackground = true,
    widthDp = 1280,
    heightDp = 720,
)
@Composable
private fun ErrorOverlayPreview() {
    AppTheme {
        CarErrorOverlay(
            error = PlayerError(
                title = "No Internet Connection",
                message = "Please check your vehicle's network connection and try again.",
            ),
            onDismiss = {},
            onRetry = {},
        )
    }
}
