package com.example.nyasaplayer.data.sync

import android.util.Log
import com.example.nyasaplayer.data.local.dao.ArtistDao
import com.example.nyasaplayer.data.local.dao.GenreDao
import com.example.nyasaplayer.data.local.dao.SongDao
import com.example.nyasaplayer.data.local.entity.ArtistEntity
import com.example.nyasaplayer.data.local.entity.GenreEntity
import com.example.nyasaplayer.data.local.entity.SongEntity
import com.example.nyasaplayer.models.Artist
import com.example.nyasaplayer.models.Genre
import com.example.nyasaplayer.models.Song
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSyncManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val genreDao: GenreDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch { collectWithRetry(songsFlow()) { songDao.replaceAll(it) } }
        scope.launch { collectWithRetry(artistsFlow()) { artistDao.replaceAll(it) } }
        scope.launch { collectWithRetry(genresFlow()) { genreDao.replaceAll(it) } }
    }

    private fun songsFlow(): Flow<List<SongEntity>> = callbackFlow {
        val registration = firestore.collection("songs")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val entities = snapshot?.documents
                    ?.mapNotNull { it.toObject<Song>() }
                    ?.map { SongEntity.fromDomain(it) }
                    .orEmpty()
                trySend(entities)
            }
        awaitClose { registration.remove() }
    }

    private fun artistsFlow(): Flow<List<ArtistEntity>> = callbackFlow {
        val registration = firestore.collection("artists")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val entities = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toObject<Artist>()?.copy(id = doc.id) }
                    ?.map { ArtistEntity.fromDomain(it) }
                    .orEmpty()
                trySend(entities)
            }
        awaitClose { registration.remove() }
    }

    private fun genresFlow(): Flow<List<GenreEntity>> = callbackFlow {
        val registration = firestore.collection("genres")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val entities = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toObject<Genre>()?.copy(id = doc.id) }
                    ?.map { GenreEntity.fromDomain(it) }
                    .orEmpty()
                trySend(entities)
            }
        awaitClose { registration.remove() }
    }

    private suspend fun <T> collectWithRetry(flow: Flow<List<T>>, onEach: suspend (List<T>) -> Unit) {
        flow.retry(Long.MAX_VALUE) { e ->
            Log.e(TAG, "Sync error, retrying in ${RETRY_DELAY_MS}ms", e)
            kotlinx.coroutines.delay(RETRY_DELAY_MS)
            true
        }.collect { onEach(it) }
    }

    private companion object {
        const val TAG = "FirebaseSyncManager"
        const val RETRY_DELAY_MS = 5_000L
    }
}
