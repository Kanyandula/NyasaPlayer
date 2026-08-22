package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.LikedSong
import com.example.nyasaplayer.core.common.models.PlaybackState
import com.example.nyasaplayer.core.common.models.RecentlyPlayedEntry
import com.example.nyasaplayer.core.common.models.UserProfile
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.AuthResult
import com.example.nyasaplayer.core.data.api.AuthSession
import com.example.nyasaplayer.core.data.api.GenreRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** The id [FakeAuthRepository] signs in with by default. */
const val DefaultUserId = "test-user"

class FakeGenreRepository : GenreRepository {

    val genres = MutableStateFlow<List<Genre>>(emptyList())

    /** When set, the genres flow fails instead of emitting — the catalogue-error path. */
    var genresError: Throwable? = null

    // Read at collection time, not at construction, so a retry after clearing the error
    // collects a working flow.
    override fun getGenres(): Flow<List<Genre>> = flow {
        genresError?.let { throw it }
        emitAll(genres)
    }
    override suspend fun getGenreById(genreId: String): Genre? = null
    override suspend fun getGenresByPopularity(limit: Int): List<Genre> = emptyList()
}

class FakeUserRepository : UserRepository {
    /**
     * Null means "Firestore has not delivered a snapshot yet", which is the state the real
     * `callbackFlow` is in before its first `onSnapshot`. An initial `emptyList()` would emit
     * "nothing is liked" the instant a collector attaches, and no test could then observe the
     * window between opening Favourites and the liked songs arriving.
     */
    private val likedByUser = mutableMapOf<String, MutableStateFlow<List<LikedSong>?>>()

    /** The default user's liked songs. Same flow as [likedFor] with [DefaultUserId]. */
    val liked: MutableStateFlow<List<LikedSong>?> get() = likedFor(DefaultUserId)

    /** Per-account liked songs, so a user switch can be given a different list. */
    fun likedFor(userId: String): MutableStateFlow<List<LikedSong>?> =
        likedByUser.getOrPut(userId) { MutableStateFlow(null) }

    /** When set, the liked-songs flow fails instead of emitting. */
    var likedSongsFlowError: Throwable? = null

    /** Set to make the next likeSong/unlikeSong throw, so the revert path can be tested. */
    var failNextWrite: Boolean = false

    /**
     * Set to make the next likeSong/unlikeSong throw this exact throwable. Exists so a test can
     * drive the CancellationException path, which must propagate rather than be reported as a
     * failed write. Takes precedence over [failNextWrite].
     */
    var throwOnNextWrite: Throwable? = null

    var likeCallCount = 0
    var unlikeCallCount = 0

    override fun getLikedSongs(userId: String): Flow<List<LikedSong>> = flow {
        likedSongsFlowError?.let { throw it }
        emitAll(likedFor(userId).filterNotNull())
    }

    override suspend fun likeSong(userId: String, mediaId: String) {
        likeCallCount++
        failIfArmed()
    }

    override suspend fun unlikeSong(userId: String, mediaId: String) {
        unlikeCallCount++
        failIfArmed()
    }

    private fun failIfArmed() {
        throwOnNextWrite?.let { armed ->
            throwOnNextWrite = null
            throw armed
        }
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
 *
 * [currentUserId] is a `var` so a test can switch accounts mid-run and drive
 * `reloadUserContent()`'s clearing path — the interface declares it a `val`, which a `var`
 * override satisfies.
 */
class FakeAuthRepository(userId: String? = DefaultUserId) : AuthRepository {

    /**
     * The snapshot API and the live flow read the same value, so a test cannot leave them
     * disagreeing about who is signed in — which is the very confusion T2 exists to remove.
     */
    val sessions = MutableStateFlow(AuthSession(userId = userId))

    override val authSession: Flow<AuthSession> = sessions

    override var currentUserId: String?
        get() = sessions.value.userId
        set(value) {
            sessions.value = sessions.value.copy(userId = value)
        }

    /** A session appearing or being revoked elsewhere, as Firebase's listener would report it. */
    fun emitSession(userId: String?, displayName: String = "") {
        sessions.value = AuthSession(userId = userId, displayName = displayName)
    }

    override val currentUser: FirebaseUser? = null
    override val isAuthenticated: Boolean get() = sessions.value.isAuthenticated
    override suspend fun signInWithEmail(email: String, password: String): AuthResult =
        AuthResult.Error("unused")
    override suspend fun signUpWithEmail(email: String, password: String): AuthResult =
        AuthResult.Error("unused")
    override suspend fun signInWithCredential(credential: AuthCredential): AuthResult =
        AuthResult.Error("unused")
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = Result.success(Unit)

    override fun signOut() {
        emitSession(userId = null)
    }
}
