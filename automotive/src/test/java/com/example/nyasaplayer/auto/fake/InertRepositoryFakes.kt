package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.LikedSong
import com.example.nyasaplayer.core.common.models.PlaybackState
import com.example.nyasaplayer.core.common.models.RecentlyPlayedEntry
import com.example.nyasaplayer.core.common.models.UserProfile
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.AuthResult
import com.example.nyasaplayer.core.data.api.GenreRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeGenreRepository : GenreRepository {
    override fun getGenres(): Flow<List<Genre>> = flowOf(emptyList())
    override suspend fun getGenreById(genreId: String): Genre? = null
    override suspend fun getGenresByPopularity(limit: Int): List<Genre> = emptyList()
}

class FakeUserRepository : UserRepository {
    val liked = MutableStateFlow<List<LikedSong>>(emptyList())

    /** Set to make the next likeSong/unlikeSong throw, so the revert path can be tested. */
    var failNextWrite: Boolean = false

    var likeCallCount = 0
    var unlikeCallCount = 0

    override fun getLikedSongs(userId: String): Flow<List<LikedSong>> = liked

    override suspend fun likeSong(userId: String, mediaId: String) {
        likeCallCount++
        if (failNextWrite) {
            failNextWrite = false
            error("write failed")
        }
    }

    override suspend fun unlikeSong(userId: String, mediaId: String) {
        unlikeCallCount++
        if (failNextWrite) {
            failNextWrite = false
            error("write failed")
        }
    }

    override fun getUserProfile(userId: String): Flow<UserProfile?> = flowOf(null)
    override suspend fun createOrUpdateProfile(profile: UserProfile) = Unit
    override fun isLiked(userId: String, mediaId: String): Flow<Boolean> = flowOf(false)
    override fun getRecentlyPlayed(userId: String, limit: Int): Flow<List<RecentlyPlayedEntry>> =
        flowOf(emptyList())
    override suspend fun logRecentlyPlayed(userId: String, mediaId: String) = Unit
    override suspend fun savePlaybackState(userId: String, state: PlaybackState) = Unit
    override suspend fun getPlaybackState(userId: String): PlaybackState? = null
}

/**
 * [currentUserId] is the only member any A3 test reads. [currentUser] stays null because
 * `FirebaseUser` is an abstract SDK class with no constructible form — the reason
 * `currentUserId` was added to the interface in Task 2.
 */
class FakeAuthRepository(override val currentUserId: String? = "test-user") : AuthRepository {
    override val currentUser: FirebaseUser? = null
    override val isAuthenticated: Boolean get() = currentUserId != null
    override suspend fun signInWithEmail(email: String, password: String): AuthResult =
        AuthResult.Error("unused")
    override suspend fun signUpWithEmail(email: String, password: String): AuthResult =
        AuthResult.Error("unused")
    override suspend fun signInWithCredential(credential: AuthCredential): AuthResult =
        AuthResult.Error("unused")
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = Result.success(Unit)
    override fun signOut() = Unit
}
