package com.example.nyasaplayer.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.nyasaplayer.core.data.local.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums")
    fun getAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :albumId")
    suspend fun getById(albumId: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE artist_id = :artistId")
    fun getByArtistId(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY popularity DESC LIMIT :limit")
    suspend fun getByPopularity(limit: Int): List<AlbumEntity>

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Transaction
    suspend fun sync(albums: List<AlbumEntity>) {
        upsertAll(albums)
        deleteNotIn(albums.map { it.id })
    }
}
