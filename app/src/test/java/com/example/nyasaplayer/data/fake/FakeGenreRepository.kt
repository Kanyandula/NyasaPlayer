package com.example.nyasaplayer.data.fake

import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.data.api.GenreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGenreRepository : GenreRepository {

    val genres = MutableStateFlow<List<Genre>>(emptyList())

    override fun getGenres(): Flow<List<Genre>> = genres
}
