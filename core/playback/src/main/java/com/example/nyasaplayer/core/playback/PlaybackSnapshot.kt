package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.Song

data class PlaybackSnapshot(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val isShuffled: Boolean = false,
    val queueSize: Int = 0,
    val queue: List<Song> = emptyList(),
    val currentQueueIndex: Int = -1,
)
