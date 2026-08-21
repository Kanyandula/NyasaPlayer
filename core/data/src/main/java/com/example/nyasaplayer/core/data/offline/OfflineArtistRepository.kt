package com.example.nyasaplayer.core.data.offline

import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.data.api.ArtistRepository
import com.example.nyasaplayer.core.data.local.dao.ArtistDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineArtistRepository @Inject constructor(
    private val artistDao: ArtistDao,
) : ArtistRepository {

    override fun getArtists(): Flow<List<Artist>> =
        artistDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getArtistById(artistId: String): Artist? =
        artistDao.getById(artistId)?.toDomain()

    override suspend fun getArtistsByPopularity(limit: Int): List<Artist> =
        artistDao.getByPopularity(limit).map { it.toDomain() }

    override suspend fun searchArtists(query: String, limit: Int): List<Artist> =
        artistDao.search(escapeLikeArgument(query), limit).map { it.toDomain() }
}
