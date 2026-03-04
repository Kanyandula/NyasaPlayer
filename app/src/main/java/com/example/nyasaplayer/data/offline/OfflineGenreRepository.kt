package com.example.nyasaplayer.data.offline

import com.example.nyasaplayer.data.api.GenreRepository
import com.example.nyasaplayer.data.local.dao.GenreDao
import com.example.nyasaplayer.models.Genre
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineGenreRepository @Inject constructor(
    private val genreDao: GenreDao,
) : GenreRepository {

    override fun getGenres(): Flow<List<Genre>> =
        genreDao.getAll().map { entities -> entities.map { it.toDomain() } }
}
