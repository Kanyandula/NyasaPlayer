package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.data.api.ArtistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeArtistRepository : ArtistRepository {

    val artists = MutableStateFlow<List<Artist>>(emptyList())

    /** When set, [searchArtists] throws it instead of returning matches. */
    var searchFailure: Throwable? = null

    override fun getArtists(): Flow<List<Artist>> = artists

    override suspend fun getArtistById(artistId: String): Artist? =
        artists.value.firstOrNull { it.id == artistId }

    override suspend fun getArtistsByPopularity(limit: Int): List<Artist> =
        artists.value.sortedByDescending { it.popularity }.take(limit)

    // Name only, like the DAO query. Ordering is the coordinator's job, so this does not rank.
    override suspend fun searchArtists(query: String, limit: Int): List<Artist> {
        searchFailure?.let { throw it }
        return artists.value.filter { it.name.contains(query, ignoreCase = true) }.take(limit)
    }
}
