package com.example.nyasaplayer.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.nyasaplayer.core.data.local.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {

    @Query("SELECT * FROM artists")
    fun getAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :artistId")
    suspend fun getById(artistId: String): ArtistEntity?

    @Upsert
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Transaction
    suspend fun sync(artists: List<ArtistEntity>) {
        upsertAll(artists)
        deleteNotIn(artists.map { it.id })
    }
}
