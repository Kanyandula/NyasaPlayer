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

    /**
     * Albums matching [query] on name, or secondarily on artist name, best match first.
     *
     * [query] must already be LIKE-escaped — see `escapeLikeArgument`. The ordering is in SQL
     * rather than in the repository because LIMIT has to keep the *best* rows, not the first
     * ones the table happens to yield.
     */
    @Query(
        """
        SELECT * FROM albums
        WHERE name LIKE '%' || :query || '%' ESCAPE '\'
           OR artist_name LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY
            CASE
                WHEN name LIKE :query ESCAPE '\' THEN 0
                WHEN name LIKE :query || '%' ESCAPE '\' THEN 1
                WHEN name LIKE '%' || :query || '%' ESCAPE '\' THEN 2
                ELSE 3
            END,
            popularity DESC,
            name COLLATE NOCASE,
            id
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int): List<AlbumEntity>

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
