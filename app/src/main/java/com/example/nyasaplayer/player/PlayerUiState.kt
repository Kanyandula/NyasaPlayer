package com.example.nyasaplayer.player

import com.example.nyasaplayer.models.Song

sealed interface PlayerMode {
    data object Hidden : PlayerMode
    data object Mini : PlayerMode
    data object Expanded : PlayerMode
}

enum class RepeatMode { Off, All, One }

data class PlayerError(
    val title: String,
    val message: String,
    val isPlaybackError: Boolean = true,
)

data class PlayerUiState(
    val playerMode: PlayerMode = PlayerMode.Hidden,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val isCurrentSongLiked: Boolean = false,
    val isShuffled: Boolean = false,
    val error: PlayerError? = null,
)
