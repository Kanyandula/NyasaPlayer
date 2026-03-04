package com.example.nyasaplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.nyasaplayer.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs")
    fun getAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE media_id IN (:mediaIds)")
    suspend fun getByMediaIds(mediaIds: List<String>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE artist_id = :artistId")
    fun getByArtistId(artistId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE genre_ids LIKE '%|' || :genreId || '|%'")
    fun getByGenreId(genreId: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(songs: List<SongEntity>) {
        deleteAll()
        insertAll(songs)
    }
}
