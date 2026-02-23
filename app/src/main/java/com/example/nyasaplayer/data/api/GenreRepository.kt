package com.example.nyasaplayer.data.api

import com.example.nyasaplayer.models.Genre
import kotlinx.coroutines.flow.Flow

interface GenreRepository {
    fun getGenres(): Flow<List<Genre>>
}
