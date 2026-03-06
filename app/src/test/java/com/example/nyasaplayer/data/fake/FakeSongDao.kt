package com.example.nyasaplayer.data.fake

import com.example.nyasaplayer.core.data.local.dao.SongDao
import com.example.nyasaplayer.core.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSongDao : SongDao {

    private val songs = MutableStateFlow<List<SongEntity>>(emptyList())

    override fun getAll(): Flow<List<SongEntity>> = songs

    override suspend fun getByMediaIds(mediaIds: List<String>): List<SongEntity> {
        val idSet = mediaIds.toSet()
        return songs.value.filter { it.mediaId in idSet }
    }

    override fun getByArtistId(artistId: String): Flow<List<SongEntity>> =
        songs.map { list -> list.filter { it.artistId == artistId } }

    override fun getByGenreId(genreId: String): Flow<List<SongEntity>> =
        songs.map { list -> list.filter { genreId in it.genreIds } }

    override suspend fun upsertAll(songs: List<SongEntity>) {
        val incoming = songs.associateBy { it.mediaId }
        val current = this.songs.value.associateBy { it.mediaId }
        this.songs.value = (current + incoming).values.toList()
    }

    override suspend fun deleteNotIn(ids: List<String>) {
        val keep = ids.toSet()
        songs.value = songs.value.filter { it.mediaId in keep }
    }

    override suspend fun sync(songs: List<SongEntity>) {
        upsertAll(songs)
        deleteNotIn(songs.map { it.mediaId })
    }

    fun setSongs(entities: List<SongEntity>) {
        songs.value = entities
    }
}
