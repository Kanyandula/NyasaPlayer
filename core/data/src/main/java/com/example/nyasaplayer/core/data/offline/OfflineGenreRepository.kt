package com.example.nyasaplayer.core.data.offline

import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.data.api.GenreRepository
import com.example.nyasaplayer.core.data.local.dao.GenreDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineGenreRepository @Inject constructor(
    private val genreDao: GenreDao,
) : GenreRepository {

    override fun getGenres(): Flow<List<Genre>> =
        genreDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getGenresByPopularity(limit: Int): List<Genre> =
        genreDao.getByPopularity(limit).map { it.toDomain() }
}
