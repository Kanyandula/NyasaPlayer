package com.example.nyasaplayer.core.data.offline

import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.data.api.AlbumRepository
import com.example.nyasaplayer.core.data.local.dao.AlbumDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineAlbumRepository @Inject constructor(
    private val albumDao: AlbumDao,
) : AlbumRepository {

    override fun getAlbums(): Flow<List<Album>> =
        albumDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAlbumById(albumId: String): Album? =
        albumDao.getById(albumId)?.toDomain()

    override fun getAlbumsByArtist(artistId: String): Flow<List<Album>> =
        albumDao.getByArtistId(artistId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAlbumsByPopularity(limit: Int): List<Album> =
        albumDao.getByPopularity(limit).map { it.toDomain() }

    override suspend fun searchAlbums(query: String, limit: Int): List<Album> =
        albumDao.search(escapeLikeArgument(query), limit).map { it.toDomain() }
}
