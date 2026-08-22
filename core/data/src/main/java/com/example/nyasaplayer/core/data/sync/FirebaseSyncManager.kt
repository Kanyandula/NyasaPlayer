package com.example.nyasaplayer.core.data.sync

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.local.dao.AlbumDao
import com.example.nyasaplayer.core.data.local.dao.ArtistDao
import com.example.nyasaplayer.core.data.local.dao.GenreDao
import com.example.nyasaplayer.core.data.local.dao.SongDao
import com.example.nyasaplayer.core.data.local.entity.AlbumEntity
import com.example.nyasaplayer.core.data.local.entity.ArtistEntity
import com.example.nyasaplayer.core.data.local.entity.GenreEntity
import com.example.nyasaplayer.core.data.local.entity.SongEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSyncManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val genreDao: GenreDao,
    private val albumDao: AlbumDao,
) : CatalogSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    @Synchronized
    override fun start() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            launch { collectWithRetry(songsFlow()) { songDao.sync(it) } }
            launch { collectWithRetry(artistsFlow()) { artistDao.sync(it) } }
            launch { collectWithRetry(genresFlow()) { genreDao.sync(it) } }
            launch { collectWithRetry(albumsFlow()) { albumDao.sync(it) } }
        }
    }

    @Synchronized
    override fun stop() {
        syncJob?.cancel()
        syncJob = null
    }

    // Songs use Firestore's toObject<Song>() directly — doc IDs (e.g. "song") differ from
    // the mediaId field (e.g. "8") because songs are keyed by mediaId in Room.
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

    private fun albumsFlow(): Flow<List<AlbumEntity>> = callbackFlow {
        val registration = firestore.collection("albums")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val entities = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toObject<Album>()?.copy(id = doc.id) }
                    ?.map { AlbumEntity.fromDomain(it) }
                    .orEmpty()
                trySend(entities)
            }
        awaitClose { registration.remove() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> collectWithRetry(flow: Flow<List<T>>, onEach: suspend (List<T>) -> Unit) {
        try {
            flow.retryWhen { cause, attempt ->
                if (isPermanentError(cause) || attempt >= MAX_RETRIES) {
                    Log.e(TAG, "Sync error, giving up after ${attempt + 1} attempts", cause)
                    false
                } else {
                    val delayMs = calculateBackoff(attempt)
                    Log.e(TAG, "Sync error, retrying in ${delayMs}ms (attempt ${attempt + 1}/$MAX_RETRIES)", cause)
                    delay(delayMs)
                    true
                }
            }.collect { onEach(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Sync permanently failed", e)
        }
    }

    @VisibleForTesting
    internal companion object {
        const val TAG = "FirebaseSyncManager"
        const val MAX_RETRIES = 20
        const val INITIAL_RETRY_DELAY_MS = 5_000L
        const val MAX_RETRY_DELAY_MS = 300_000L
        const val BACKOFF_SHIFT_CAP = 6L
        val PERMANENT_ERROR_CODES = setOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.NOT_FOUND,
            FirebaseFirestoreException.Code.UNAUTHENTICATED,
        )

        fun calculateBackoff(attempt: Long): Long {
            val shift = attempt.coerceAtMost(BACKOFF_SHIFT_CAP).toInt()
            return (INITIAL_RETRY_DELAY_MS * (1L shl shift)).coerceAtMost(MAX_RETRY_DELAY_MS)
        }

        fun isPermanentError(e: Throwable): Boolean =
            e is FirebaseFirestoreException && e.code in PERMANENT_ERROR_CODES
    }
}
