package com.example.nyasaplayer.core.data.fake

import com.example.nyasaplayer.core.data.local.dao.AlbumDao
import com.example.nyasaplayer.core.data.local.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAlbumDao : AlbumDao {

    private val albums = MutableStateFlow<List<AlbumEntity>>(emptyList())

    override fun getAll(): Flow<List<AlbumEntity>> = albums

    override suspend fun getById(albumId: String): AlbumEntity? =
        albums.value.find { it.id == albumId }

    override fun getByArtistId(artistId: String): Flow<List<AlbumEntity>> =
        albums.map { list -> list.filter { it.artistId == artistId } }

    override suspend fun getByPopularity(limit: Int): List<AlbumEntity> =
        albums.value.sortedByDescending { it.popularity }.take(limit)

    // Receives an already-escaped query, like the real DAO; tests here use plain words, so the
    // escape sequences never reach this contains().
    override suspend fun search(query: String, limit: Int): List<AlbumEntity> {
        val needle = query.lowercase()
        return albums.value.filter {
            it.name.lowercase().contains(needle) || it.artistName.lowercase().contains(needle)
        }.sortedByDescending { it.popularity }.take(limit)
    }

    override suspend fun upsertAll(albums: List<AlbumEntity>) {
        val incoming = albums.associateBy { it.id }
        val current = this.albums.value.associateBy { it.id }
        this.albums.value = (current + incoming).values.toList()
    }

    override suspend fun deleteNotIn(ids: List<String>) {
        val keep = ids.toSet()
        albums.value = albums.value.filter { it.id in keep }
    }

    override suspend fun sync(albums: List<AlbumEntity>) {
        upsertAll(albums)
        deleteNotIn(albums.map { it.id })
    }

    fun setAlbums(entities: List<AlbumEntity>) {
        albums.value = entities
    }
}
