package com.example.nyasaplayer.core.data

import com.example.nyasaplayer.core.common.models.LikedSong
import com.example.nyasaplayer.core.common.models.PlaybackState
import com.example.nyasaplayer.core.common.models.RecentlyPlayedEntry
import com.example.nyasaplayer.core.common.models.UserProfile
import com.example.nyasaplayer.core.data.api.UserRepository
import com.example.nyasaplayer.core.data.dto.FirestoreLikedSongDto
import com.example.nyasaplayer.core.data.dto.FirestorePlaybackStateDto
import com.example.nyasaplayer.core.data.dto.FirestoreRecentlyPlayedDto
import com.example.nyasaplayer.core.data.dto.FirestoreUserProfileDto
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
                trySend(snapshot?.toObject<FirestoreUserProfileDto>()?.toDomain())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun createOrUpdateProfile(profile: UserProfile) {
        userDoc(profile.userId).set(FirestoreUserProfileDto.fromDomain(profile), SetOptions.merge()).await()
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
                val songs = snapshot?.documents
                    ?.mapNotNull { it.toObject<FirestoreLikedSongDto>()?.toDomain() }
                    .orEmpty()
                trySend(songs)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun likeSong(userId: String, mediaId: String) {
        val dto = FirestoreLikedSongDto(mediaId = mediaId, likedAt = Timestamp.now())
        userDoc(userId).collection("likedSongs").document(mediaId).set(dto).await()
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
                        snapshot?.documents?.mapNotNull { it.toObject<FirestoreRecentlyPlayedDto>()?.toDomain() }
                            .orEmpty()
                    trySend(entries)
                }
            awaitClose { registration.remove() }
        }

    override suspend fun logRecentlyPlayed(userId: String, mediaId: String) {
        val dto = FirestoreRecentlyPlayedDto(mediaId = mediaId, playedAt = Timestamp.now())
        userDoc(userId).collection("recentlyPlayed").add(dto).await()
    }

    // ── Playback State ──

    override suspend fun savePlaybackState(userId: String, state: PlaybackState) {
        userDoc(userId).collection("playbackState").document("current")
            .set(FirestorePlaybackStateDto.fromDomain(state)).await()
    }

    override suspend fun getPlaybackState(userId: String): PlaybackState? {
        return try {
            userDoc(userId).collection("playbackState").document("current")
                .get().await().toObject<FirestorePlaybackStateDto>()?.toDomain()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
