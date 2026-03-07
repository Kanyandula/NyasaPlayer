package com.example.nyasaplayer.core.data.fake

import com.example.nyasaplayer.core.common.models.LikedSong
import com.example.nyasaplayer.core.common.models.PlaybackState
import com.example.nyasaplayer.core.common.models.RecentlyPlayedEntry
import com.example.nyasaplayer.core.common.models.UserProfile
import com.example.nyasaplayer.core.data.api.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeUserRepository : UserRepository {

    val profiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val likedSongs = MutableStateFlow<Map<String, List<LikedSong>>>(emptyMap())
    val recentlyPlayed = MutableStateFlow<Map<String, List<RecentlyPlayedEntry>>>(emptyMap())
    val playbackStates = MutableStateFlow<Map<String, PlaybackState>>(emptyMap())

    override fun getUserProfile(userId: String): Flow<UserProfile?> =
        profiles.map { it[userId] }

    override suspend fun createOrUpdateProfile(profile: UserProfile) {
        profiles.update { it + (profile.userId to profile) }
    }

    override fun getLikedSongs(userId: String): Flow<List<LikedSong>> =
        likedSongs.map { it[userId].orEmpty() }

    override suspend fun likeSong(userId: String, mediaId: String) {
        likedSongs.update { map ->
            val current = map[userId].orEmpty().filter { it.mediaId != mediaId }
            map + (userId to (current + LikedSong(mediaId = mediaId)))
        }
    }

    override suspend fun unlikeSong(userId: String, mediaId: String) {
        likedSongs.update { map ->
            val current = map[userId].orEmpty()
            map + (userId to current.filter { it.mediaId != mediaId })
        }
    }

    override fun isLiked(userId: String, mediaId: String): Flow<Boolean> =
        likedSongs.map { map -> map[userId].orEmpty().any { it.mediaId == mediaId } }

    override fun getRecentlyPlayed(userId: String, limit: Int): Flow<List<RecentlyPlayedEntry>> =
        recentlyPlayed.map { it[userId].orEmpty().take(limit) }

    override suspend fun logRecentlyPlayed(userId: String, mediaId: String) {
        recentlyPlayed.update { map ->
            val current = map[userId].orEmpty()
            map + (userId to (listOf(RecentlyPlayedEntry(mediaId = mediaId)) + current))
        }
    }

    override suspend fun savePlaybackState(userId: String, state: PlaybackState) {
        playbackStates.update { it + (userId to state) }
    }

    override suspend fun getPlaybackState(userId: String): PlaybackState? =
        playbackStates.value[userId]
}
