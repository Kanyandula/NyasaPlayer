package com.example.nyasaplayer.models

import com.google.firebase.Timestamp

data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val createdAt: Timestamp? = null,
    val accountType: String = "free",
)

data class LikedSong(
    val mediaId: String = "",
    val likedAt: Timestamp? = null,
)

data class RecentlyPlayedEntry(
    val mediaId: String = "",
    val playedAt: Timestamp? = null,
)

data class PlaybackState(
    val currentSongId: String = "",
    val positionMs: Long = 0L,
    val queueSongIds: List<String> = emptyList(),
    val queueIndex: Int = 0,
    val repeatMode: String = "Off",
    val savedAt: Timestamp? = null,
)
