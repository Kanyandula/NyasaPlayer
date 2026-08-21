package com.example.nyasaplayer.core.data.api

import com.example.nyasaplayer.core.common.models.Song
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun getSongs(): Flow<List<Song>>
    suspend fun getSongsByIds(ids: List<String>): List<Song>

    // Used by future artist/genre detail screens (tested in OfflineSongRepositoryTest)
    fun getSongsByArtist(artistId: String): Flow<List<Song>>
    fun getSongsByGenre(genreId: String): Flow<List<Song>>

    // Used by AAOS browse tree
    suspend fun getSongsByPopularity(limit: Int): List<Song>

    /**
     * Songs matching [query], most popular first.
     *
     * Implementations trim [query] and treat LIKE wildcards in it as literal characters, so the
     * car launcher and Assistant cannot answer the same words differently (spec 3.5).
     */
    suspend fun searchSongs(query: String, limit: Int): List<Song>
}
