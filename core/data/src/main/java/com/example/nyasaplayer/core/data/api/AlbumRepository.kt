package com.example.nyasaplayer.core.data.api

import com.example.nyasaplayer.core.common.models.Album
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun getAlbums(): Flow<List<Album>>
    suspend fun getAlbumById(albumId: String): Album?
    fun getAlbumsByArtist(artistId: String): Flow<List<Album>>
    suspend fun getAlbumsByPopularity(limit: Int): List<Album>
}
