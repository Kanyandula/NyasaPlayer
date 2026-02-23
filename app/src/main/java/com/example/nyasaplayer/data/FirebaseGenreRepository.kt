package com.example.nyasaplayer.data

import com.example.nyasaplayer.data.api.GenreRepository
import com.example.nyasaplayer.models.Genre
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class FirebaseGenreRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : GenreRepository {
    override fun getGenres(): Flow<List<Genre>> = callbackFlow {
        val registration = firestore.collection("genres")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val genres = snapshot?.documents?.mapNotNull {
                    it.toObject<Genre>()?.copy(id = it.id)
                }.orEmpty()
                trySend(genres)
            }
        awaitClose { registration.remove() }
    }
}
