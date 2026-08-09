package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.data.api.AlbumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAlbumRepository : AlbumRepository {

    val albums = MutableStateFlow<List<Album>>(emptyList())

    override fun getAlbums(): Flow<List<Album>> = albums

    override suspend fun getAlbumById(albumId: String): Album? =
        albums.value.firstOrNull { it.id == albumId }

    override fun getAlbumsByArtist(artistId: String): Flow<List<Album>> = albums

    override suspend fun getAlbumsByPopularity(limit: Int): List<Album> = albums.value.take(limit)
}
