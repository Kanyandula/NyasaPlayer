package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.SongRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class FakeSongRepository : SongRepository {

    val songs = MutableStateFlow<List<Song>>(emptyList())

    /** Suspends every [getSongsByIds] call until released. Lets a test hold a load in flight. */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override fun getSongs(): Flow<List<Song>> = songs

    override suspend fun getSongsByIds(ids: List<String>): List<Song> {
        // NonCancellable so a gated call resumes and runs on to its caller's state write even
        // after that caller's job was cancelled. A plain await() is a cancellable suspension
        // point, which would make cancel() alone discard the stale result and leave
        // openDetail's guard — the thing these tests exist to cover — untested.
        withContext(NonCancellable) { gate?.await() }
        val byId = songs.value.associateBy { it.mediaId }
        // Request order, matching OfflineSongRepository's contract.
        return ids.mapNotNull { byId[it] }
    }

    override fun getSongsByArtist(artistId: String): Flow<List<Song>> = songs
    override fun getSongsByGenre(genreId: String): Flow<List<Song>> = songs
    override suspend fun getSongsByPopularity(limit: Int): List<Song> = songs.value.take(limit)
    override suspend fun searchSongs(query: String, limit: Int): List<Song> = emptyList()
}
