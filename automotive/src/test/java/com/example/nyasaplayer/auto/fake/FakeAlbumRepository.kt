package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.data.api.AlbumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAlbumRepository : AlbumRepository {

    val albums = MutableStateFlow<List<Album>>(emptyList())

    /** Counts [getAlbumById] calls so a test can assert a repeated load did not re-fetch. */
    var getAlbumByIdCallCount = 0
        private set

    override fun getAlbums(): Flow<List<Album>> = albums

    override suspend fun getAlbumById(albumId: String): Album? {
        getAlbumByIdCallCount++
        return albums.value.firstOrNull { it.id == albumId }
    }

    override fun getAlbumsByArtist(artistId: String): Flow<List<Album>> = albums

    override suspend fun getAlbumsByPopularity(limit: Int): List<Album> = albums.value.take(limit)

    // Filters but does not rank: match-quality ordering is the DAO's, and CatalogSearchDaoTest
    // owns it against real SQLite. Nothing in :automotive asserts this section's order.
    override suspend fun searchAlbums(query: String, limit: Int): List<Album> {
        val needle = query.trim().lowercase()
        return albums.value.filter {
            it.name.lowercase().contains(needle) || it.artistName.lowercase().contains(needle)
        }.take(limit)
    }
}
