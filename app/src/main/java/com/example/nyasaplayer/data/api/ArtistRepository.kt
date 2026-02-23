package com.example.nyasaplayer.data.api

import com.example.nyasaplayer.models.Artist
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    fun getArtists(): Flow<List<Artist>>
    suspend fun getArtistById(artistId: String): Artist?
}
