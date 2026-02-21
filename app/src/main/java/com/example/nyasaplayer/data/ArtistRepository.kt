package com.example.nyasaplayer.data

import com.example.nyasaplayer.models.Artist
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    fun getArtists(): Flow<List<Artist>> = callbackFlow {
        val registration = firestore.collection("artists")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val artists = snapshot?.documents?.mapNotNull {
                    it.toObject<Artist>()?.copy(id = it.id)
                }.orEmpty()
                trySend(artists)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getArtistById(artistId: String): Artist? {
        val doc = firestore.collection("artists")
            .document(artistId)
            .get()
            .await()
        return doc.toObject<Artist>()?.copy(id = doc.id)
    }
}
