package com.example.nyasaplayer.data

import com.example.nyasaplayer.data.api.SongRepository
import com.example.nyasaplayer.models.Song
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class FirebaseSongRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SongRepository {
    private companion object {
        const val FIRESTORE_WHERE_IN_LIMIT = 30
    }

    override fun getSongs(): Flow<List<Song>> = callbackFlow {
        val registration = firestore.collection("songs")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val songs = snapshot?.documents?.mapNotNull { it.toObject<Song>() }.orEmpty()
                trySend(songs)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun getSongsByIds(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        return try {
            ids.chunked(FIRESTORE_WHERE_IN_LIMIT).flatMap { chunk ->
                firestore.collection("songs")
                    .whereIn("mediaId", chunk)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.toObject<Song>() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun getSongsByArtist(artistId: String): Flow<List<Song>> = callbackFlow {
        val registration = firestore.collection("songs")
            .whereEqualTo("artistId", artistId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val songs = snapshot?.documents?.mapNotNull { it.toObject<Song>() }.orEmpty()
                trySend(songs)
            }
        awaitClose { registration.remove() }
    }

    override fun getSongsByGenre(genreId: String): Flow<List<Song>> = callbackFlow {
        val registration = firestore.collection("songs")
            .whereArrayContains("genreIds", genreId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val songs = snapshot?.documents?.mapNotNull { it.toObject<Song>() }.orEmpty()
                trySend(songs)
            }
        awaitClose { registration.remove() }
    }
}
