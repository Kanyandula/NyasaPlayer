package com.example.nyasaplayer.data

import com.example.nyasaplayer.data.api.UserRepository
import com.example.nyasaplayer.models.LikedSong
import com.example.nyasaplayer.models.PlaybackState
import com.example.nyasaplayer.models.RecentlyPlayedEntry
import com.example.nyasaplayer.models.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class FirebaseUserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UserRepository {
    private fun userDoc(userId: String) = firestore.collection("users").document(userId)

    // ── Profile ──

    override fun getUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val registration = userDoc(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject<UserProfile>())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun createOrUpdateProfile(profile: UserProfile) {
        userDoc(profile.userId).set(profile, SetOptions.merge()).await()
    }

    // ── Liked Songs ──

    override fun getLikedSongs(userId: String): Flow<List<LikedSong>> = callbackFlow {
        val registration = userDoc(userId).collection("likedSongs")
            .orderBy("likedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val songs = snapshot?.documents?.mapNotNull { it.toObject<LikedSong>() }.orEmpty()
                trySend(songs)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun likeSong(userId: String, mediaId: String) {
        val likedSong = LikedSong(mediaId = mediaId, likedAt = Timestamp.now())
        userDoc(userId).collection("likedSongs").document(mediaId).set(likedSong).await()
    }

    override suspend fun unlikeSong(userId: String, mediaId: String) {
        userDoc(userId).collection("likedSongs").document(mediaId).delete().await()
    }

    override fun isLiked(userId: String, mediaId: String): Flow<Boolean> = callbackFlow {
        val registration = userDoc(userId).collection("likedSongs").document(mediaId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.exists() == true)
            }
        awaitClose { registration.remove() }
    }

    // ── Recently Played ──

    override fun getRecentlyPlayed(userId: String, limit: Int): Flow<List<RecentlyPlayedEntry>> =
        callbackFlow {
            val registration = userDoc(userId).collection("recentlyPlayed")
                .orderBy("playedAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val entries =
                        snapshot?.documents?.mapNotNull { it.toObject<RecentlyPlayedEntry>() }
                            .orEmpty()
                    trySend(entries)
                }
            awaitClose { registration.remove() }
        }

    override suspend fun logRecentlyPlayed(userId: String, mediaId: String) {
        val entry = RecentlyPlayedEntry(mediaId = mediaId, playedAt = Timestamp.now())
        userDoc(userId).collection("recentlyPlayed").add(entry).await()
    }

    // ── Playback State ──

    override suspend fun savePlaybackState(userId: String, state: PlaybackState) {
        userDoc(userId).collection("playbackState").document("current").set(state).await()
    }

    override suspend fun getPlaybackState(userId: String): PlaybackState? {
        return try {
            userDoc(userId).collection("playbackState").document("current")
                .get().await().toObject<PlaybackState>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
