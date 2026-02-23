package com.example.nyasaplayer.data.fake

import com.example.nyasaplayer.data.api.GenreRepository
import com.example.nyasaplayer.models.Genre
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGenreRepository : GenreRepository {

    val genres = MutableStateFlow<List<Genre>>(emptyList())

    override fun getGenres(): Flow<List<Genre>> = genres
}
