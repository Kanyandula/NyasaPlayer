package com.example.nyasaplayer.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.nyasaplayer.core.data.local.entity.GenreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GenreDao {

    @Query("SELECT * FROM genres")
    fun getAll(): Flow<List<GenreEntity>>

    @Query("SELECT * FROM genres WHERE id = :genreId")
    suspend fun getById(genreId: String): GenreEntity?

    @Query("SELECT * FROM genres ORDER BY popularity DESC LIMIT :limit")
    suspend fun getByPopularity(limit: Int): List<GenreEntity>

    @Upsert
    suspend fun upsertAll(genres: List<GenreEntity>)

    @Query("DELETE FROM genres WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Transaction
    suspend fun sync(genres: List<GenreEntity>) {
        upsertAll(genres)
        deleteNotIn(genres.map { it.id })
    }
}
