package com.example.nyasaplayer.data.fake

import com.example.nyasaplayer.data.api.ArtistRepository
import com.example.nyasaplayer.models.Artist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeArtistRepository : ArtistRepository {

    val artists = MutableStateFlow<List<Artist>>(emptyList())

    override fun getArtists(): Flow<List<Artist>> = artists

    override suspend fun getArtistById(artistId: String): Artist? =
        artists.value.find { it.id == artistId }
}
