package com.example.nyasaplayer.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.nyasaplayer.core.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs")
    fun getAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE media_id IN (:mediaIds)")
    suspend fun getByMediaIds(mediaIds: List<String>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE artist_id = :artistId")
    fun getByArtistId(artistId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE genre_ids LIKE '%\"' || :genreId || '\"%'")
    fun getByGenreId(genreId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY popularity DESC LIMIT :limit")
    suspend fun getByPopularity(limit: Int): List<SongEntity>

    @Query(
        """
        SELECT * FROM songs
        WHERE title LIKE '%' || :query || '%'
           OR artist_name LIKE '%' || :query || '%'
        ORDER BY popularity DESC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int): List<SongEntity>

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE media_id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Transaction
    suspend fun sync(songs: List<SongEntity>) {
        upsertAll(songs)
        deleteNotIn(songs.map { it.mediaId })
    }
}
