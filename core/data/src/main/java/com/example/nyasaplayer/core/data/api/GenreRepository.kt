package com.example.nyasaplayer.core.data.api

import com.example.nyasaplayer.core.common.models.Genre
import kotlinx.coroutines.flow.Flow

interface GenreRepository {
    fun getGenres(): Flow<List<Genre>>

    // Used by AAOS browse tree
    suspend fun getGenresByPopularity(limit: Int): List<Genre>
}
