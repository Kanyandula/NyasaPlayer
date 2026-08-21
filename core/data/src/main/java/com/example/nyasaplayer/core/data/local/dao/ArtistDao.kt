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

    @Query("SELECT * FROM artists ORDER BY popularity DESC LIMIT :limit")
    suspend fun getByPopularity(limit: Int): List<ArtistEntity>

    /**
     * Artists matching [query] on name, best match first.
     *
     * Name only: `genres` is a JSON-encoded list column, and matching it would mean a LIKE over
     * serialized text that also hits ids and punctuation (spec 3.2). [query] must already be
     * LIKE-escaped — see `escapeLikeArgument`.
     */
    @Query(
        """
        SELECT * FROM artists
        WHERE name LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY
            CASE
                WHEN name LIKE :query ESCAPE '\' THEN 0
                WHEN name LIKE :query || '%' ESCAPE '\' THEN 1
                ELSE 2
            END,
            popularity DESC,
            name COLLATE NOCASE,
            id
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int): List<ArtistEntity>

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
