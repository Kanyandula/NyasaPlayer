package com.example.nyasaplayer.core.data.api

import com.example.nyasaplayer.core.common.models.Song
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun getSongs(): Flow<List<Song>>
    suspend fun getSongsByIds(ids: List<String>): List<Song>
    fun getSongsByArtist(artistId: String): Flow<List<Song>>
    fun getSongsByGenre(genreId: String): Flow<List<Song>>
}
