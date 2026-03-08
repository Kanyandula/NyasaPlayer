package com.example.nyasaplayer.core.data.fake

import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.data.api.ArtistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeArtistRepository : ArtistRepository {

    val artists = MutableStateFlow<List<Artist>>(emptyList())

    override fun getArtists(): Flow<List<Artist>> = artists

    override suspend fun getArtistById(artistId: String): Artist? =
        artists.value.find { it.id == artistId }

    override suspend fun getArtistsByPopularity(limit: Int): List<Artist> =
        artists.value.sortedByDescending { it.popularity }.take(limit)
}
