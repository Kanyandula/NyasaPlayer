package com.example.nyasaplayer.data.offline

import com.example.nyasaplayer.data.api.ArtistRepository
import com.example.nyasaplayer.data.local.dao.ArtistDao
import com.example.nyasaplayer.models.Artist
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
}
