package com.example.nyasaplayer.data

import com.example.nyasaplayer.data.api.HomeFeedRepository
import com.example.nyasaplayer.models.HomeFeed
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class FirebaseHomeFeedRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : HomeFeedRepository {
    override fun getHomeFeed(): Flow<HomeFeed?> = callbackFlow {
        val registration = firestore.collection("homeFeed")
            .document("default")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val feed = snapshot?.toObject<HomeFeed>()
                trySend(feed)
            }
        awaitClose { registration.remove() }
    }
}
