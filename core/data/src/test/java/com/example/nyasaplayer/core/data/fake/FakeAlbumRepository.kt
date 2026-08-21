package com.example.nyasaplayer.core.data.fake

import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.data.api.AlbumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAlbumRepository : AlbumRepository {

    val albums = MutableStateFlow<List<Album>>(emptyList())

    override fun getAlbums(): Flow<List<Album>> = albums

    override suspend fun getAlbumById(albumId: String): Album? =
        albums.value.find { it.id == albumId }

    override fun getAlbumsByArtist(artistId: String): Flow<List<Album>> =
        albums.map { list -> list.filter { it.artistId == artistId } }

    override suspend fun getAlbumsByPopularity(limit: Int): List<Album> =
        albums.value.sortedByDescending { it.popularity }.take(limit)

    // Filters without ranking: match-quality ordering is the DAO's, and CatalogSearchDaoTest owns
    // it against real SQLite.
    override suspend fun searchAlbums(query: String, limit: Int): List<Album> =
        albums.value.filter {
            it.name.contains(query.trim(), ignoreCase = true) ||
                it.artistName.contains(query.trim(), ignoreCase = true)
        }.take(limit)
}
