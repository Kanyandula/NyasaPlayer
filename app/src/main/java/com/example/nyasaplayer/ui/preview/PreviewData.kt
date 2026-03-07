package com.example.nyasaplayer.ui.preview

import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.player.PlayerMode
import com.example.nyasaplayer.player.PlayerUiState
import com.example.nyasaplayer.screens.home.ForYouUiState
import com.example.nyasaplayer.screens.home.ResolvedSection

val PreviewSong = Song(
    mediaId = "1",
    title = "Nzeru Zanga",
    subtitle = "Nyasa Band",
    imageUrl = "",
    songUrl = "",
    artistId = "artist_nyasa_band",
    artistName = "Nyasa Band",
    genreIds = listOf("afropop"),
)

val PreviewSongs = listOf(
    PreviewSong,
    Song(
        mediaId = "2",
        title = "Mtima Wanga",
        subtitle = "Faith Mussa",
        artistId = "artist_faith_mussa",
        artistName = "Faith Mussa",
        genreIds = listOf("gospel"),
    ),
    Song(
        mediaId = "3",
        title = "Chisomo",
        subtitle = "Skeffa Chimoto",
        artistId = "artist_skeffa_chimoto",
        artistName = "Skeffa Chimoto",
        genreIds = listOf("afropop"),
    ),
)

val PreviewSongsWithDuration = listOf(
    PreviewSong.copy(durationMs = 234_000L),
    PreviewSongs[1].copy(durationMs = 198_000L),
    PreviewSongs[2].copy(durationMs = 312_000L),
    Song(
        mediaId = "4",
        title = "Ndife Amodzi",
        subtitle = "Lulu",
        artistId = "artist_lulu",
        artistName = "Lulu",
        genreIds = listOf("rnb"),
        durationMs = 267_000L,
    ),
    Song(
        mediaId = "5",
        title = "Moyo Wanga",
        subtitle = "Patience Namadingo",
        artistId = "artist_patience",
        artistName = "Patience Namadingo",
        genreIds = listOf("gospel"),
        durationMs = 285_000L,
    ),
)

val PreviewGenres = listOf(
    Genre(id = "afropop", name = "Afropop", color = "#E91E63", songIds = listOf("1", "3")),
    Genre(id = "gospel", name = "Gospel", color = "#9C27B0", songIds = listOf("2", "5")),
    Genre(id = "rnb", name = "R&B", color = "#2196F3", songIds = listOf("4")),
    Genre(id = "hiphop", name = "Hip Hop", color = "#FF9800", songIds = emptyList()),
    Genre(id = "reggae", name = "Reggae", color = "#4CAF50", songIds = emptyList()),
    Genre(id = "jazz", name = "Jazz", color = "#795548", songIds = emptyList()),
)

private const val PreviewPositionMs = 65_000L
private const val PreviewDurationMs = 210_000L

val PreviewPlayerStatePlaying = PlayerUiState(
    playerMode = PlayerMode.Expanded,
    currentSong = PreviewSong,
    isPlaying = true,
    currentPositionMs = PreviewPositionMs,
    durationMs = PreviewDurationMs,
    hasPrevious = true,
    hasNext = true,
)

val PreviewPlayerStatePaused = PreviewPlayerStatePlaying.copy(isPlaying = false)

val PreviewPlayerStateMini = PreviewPlayerStatePlaying.copy(playerMode = PlayerMode.Mini)

val PreviewForYouUiState = ForYouUiState(
    isLoading = false,
    sections = listOf(
        ResolvedSection(
            id = "quick_picks",
            title = "Quick Picks",
            type = "grid",
            songs = PreviewSongs,
        ),
        ResolvedSection(
            id = "recommended",
            title = "Recommended",
            type = "horizontal_scroll",
            songs = PreviewSongs,
        ),
    ),
    allSongs = PreviewSongs,
    recentlyPlayed = PreviewSongs,
)
