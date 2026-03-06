package com.example.nyasaplayer.data.dto

import com.example.nyasaplayer.models.LikedSong
import com.example.nyasaplayer.models.PlaybackState
import com.example.nyasaplayer.models.RecentlyPlayedEntry
import com.example.nyasaplayer.models.UserProfile
import com.google.firebase.Timestamp
import java.util.Date

data class FirestoreUserProfileDto(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val createdAt: Timestamp? = null,
    val accountType: String = "free",
) {
    fun toDomain() = UserProfile(
        userId = userId,
        displayName = displayName,
        email = email,
        photoUrl = photoUrl,
        createdAt = createdAt?.toDate()?.time,
        accountType = accountType,
    )

    companion object {
        fun fromDomain(profile: UserProfile) = FirestoreUserProfileDto(
            userId = profile.userId,
            displayName = profile.displayName,
            email = profile.email,
            photoUrl = profile.photoUrl,
            createdAt = profile.createdAt?.let { Timestamp(Date(it)) },
            accountType = profile.accountType,
        )
    }
}

data class FirestoreLikedSongDto(
    val mediaId: String = "",
    val likedAt: Timestamp? = null,
) {
    fun toDomain() = LikedSong(
        mediaId = mediaId,
        likedAt = likedAt?.toDate()?.time,
    )
}

data class FirestoreRecentlyPlayedDto(
    val mediaId: String = "",
    val playedAt: Timestamp? = null,
) {
    fun toDomain() = RecentlyPlayedEntry(
        mediaId = mediaId,
        playedAt = playedAt?.toDate()?.time,
    )
}

data class FirestorePlaybackStateDto(
    val currentSongId: String = "",
    val positionMs: Long = 0L,
    val queueSongIds: List<String> = emptyList(),
    val queueIndex: Int = 0,
    val repeatMode: String = "Off",
    val savedAt: Timestamp? = null,
) {
    fun toDomain() = PlaybackState(
        currentSongId = currentSongId,
        positionMs = positionMs,
        queueSongIds = queueSongIds,
        queueIndex = queueIndex,
        repeatMode = repeatMode,
        savedAt = savedAt?.toDate()?.time,
    )

    companion object {
        fun fromDomain(state: PlaybackState) = FirestorePlaybackStateDto(
            currentSongId = state.currentSongId,
            positionMs = state.positionMs,
            queueSongIds = state.queueSongIds,
            queueIndex = state.queueIndex,
            repeatMode = state.repeatMode,
            savedAt = state.savedAt?.let { Timestamp(Date(it)) },
        )
    }
}
