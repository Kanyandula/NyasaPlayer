package com.example.nyasaplayer.data.api

import com.example.nyasaplayer.models.LikedSong
import com.example.nyasaplayer.models.PlaybackState
import com.example.nyasaplayer.models.RecentlyPlayedEntry
import com.example.nyasaplayer.models.UserProfile
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions") // Repository covers profile, likes, recently played, and playback state
interface UserRepository {
    fun getUserProfile(userId: String): Flow<UserProfile?>
    suspend fun createOrUpdateProfile(profile: UserProfile)
    fun getLikedSongs(userId: String): Flow<List<LikedSong>>
    suspend fun likeSong(userId: String, mediaId: String)
    suspend fun unlikeSong(userId: String, mediaId: String)
    fun isLiked(userId: String, mediaId: String): Flow<Boolean>
    fun getRecentlyPlayed(userId: String, limit: Int = 20): Flow<List<RecentlyPlayedEntry>>
    suspend fun logRecentlyPlayed(userId: String, mediaId: String)
    suspend fun savePlaybackState(userId: String, state: PlaybackState)
    suspend fun getPlaybackState(userId: String): PlaybackState?
}
