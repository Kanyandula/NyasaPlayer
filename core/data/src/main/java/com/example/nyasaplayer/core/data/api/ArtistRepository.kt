package com.example.nyasaplayer.core.data.api

import com.example.nyasaplayer.core.common.models.Artist
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    fun getArtists(): Flow<List<Artist>>
    suspend fun getArtistById(artistId: String): Artist?

    // The OEM template's browse tree
    suspend fun getArtistsByPopularity(limit: Int): List<Artist>

    /** Artists matching trimmed [query] on name, best match first. */
    suspend fun searchArtists(query: String, limit: Int): List<Artist>
}
