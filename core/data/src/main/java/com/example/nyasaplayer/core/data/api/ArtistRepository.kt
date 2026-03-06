package com.example.nyasaplayer.core.data.api

import com.example.nyasaplayer.core.common.models.Artist
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    fun getArtists(): Flow<List<Artist>>
    suspend fun getArtistById(artistId: String): Artist?
}
