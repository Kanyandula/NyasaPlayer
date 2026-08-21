package com.example.nyasaplayer.core.data

import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.data.api.PlaylistRepository
import com.example.nyasaplayer.core.data.dto.FirestorePlaylistDto
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class SongAlreadyInPlaylistException : Exception("Song already in playlist")

class FirebasePlaylistRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : PlaylistRepository {

    private fun playlistsCollection(userId: String) =
        firestore.collection("users").document(userId).collection("playlists")

    override fun getPlaylists(userId: String): Flow<List<Playlist>> = callbackFlow {
        val registration = playlistsCollection(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val playlists = snapshot?.documents
                    ?.mapNotNull { doc ->
                        doc.toObject<FirestorePlaylistDto>()?.toDomain(doc.id)
                    }
                    .orEmpty()
                trySend(playlists)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun createPlaylist(userId: String, name: String): String {
        val now = Timestamp.now()
        val dto = FirestorePlaylistDto(
            name = name,
            songIds = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
        val docRef = playlistsCollection(userId).add(dto).await()
        return docRef.id
    }

    override suspend fun addSongToPlaylist(userId: String, playlistId: String, mediaId: String) {
        val docRef = playlistsCollection(userId).document(playlistId)
        val snapshot = docRef.get().await()
        val existing = snapshot.toObject<FirestorePlaylistDto>()
        if (existing != null && mediaId in existing.songIds) {
            throw SongAlreadyInPlaylistException()
        }
        docRef.update(
            mapOf(
                "songIds" to com.google.firebase.firestore.FieldValue.arrayUnion(mediaId),
                "updatedAt" to Timestamp.now(),
            ),
        ).await()
    }

    override suspend fun removeSongFromPlaylist(userId: String, playlistId: String, mediaId: String) {
        playlistsCollection(userId).document(playlistId).update(
            mapOf(
                "songIds" to com.google.firebase.firestore.FieldValue.arrayRemove(mediaId),
                "updatedAt" to Timestamp.now(),
            ),
        ).await()
    }

    override suspend fun deletePlaylist(userId: String, playlistId: String) {
        playlistsCollection(userId).document(playlistId).delete().await()
    }

    /**
     * Firestore indexes prefixes, not substrings, so the name filter runs in memory over a
     * one-shot read of the user's own playlists — a collection this app already loads whole for
     * the library screen. It stays here rather than in a UI collector so a search cannot depend
     * on whether some screen happened to have emitted yet.
     */
    override suspend fun searchPlaylists(userId: String, query: String, limit: Int): List<Playlist> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return playlistsCollection(userId).get().await().documents
            .mapNotNull { doc -> doc.toObject<FirestorePlaylistDto>()?.toDomain(doc.id) }
            .filter { it.name.lowercase().contains(needle) }
            .sortedWith(
                compareBy({ nameMatchTier(it.name, needle) }, { it.name.lowercase() }, { it.id }),
            )
            .take(limit)
    }
}

/** Exact name, then prefix, then substring — the spec's match-quality order (3.2). */
private fun nameMatchTier(name: String, lowercaseNeedle: String): Int {
    val lowercaseName = name.lowercase()
    return when {
        lowercaseName == lowercaseNeedle -> 0
        lowercaseName.startsWith(lowercaseNeedle) -> 1
        else -> 2
    }
}
