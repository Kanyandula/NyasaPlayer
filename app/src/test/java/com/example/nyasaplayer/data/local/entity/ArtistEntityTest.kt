package com.example.nyasaplayer.core.data.local.entity

import com.example.nyasaplayer.core.common.models.Artist
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistEntityTest {

    private val fullArtist = Artist(
        id = "a1",
        name = "Test Artist",
        imageUrl = "https://img/a1",
        genres = listOf("rock", "pop"),
        popularity = 85,
        songCount = 12,
    )

    @Test
    fun toDomain_mapsAllFields() {
        val entity = ArtistEntity.fromDomain(fullArtist)
        val domain = entity.toDomain()

        assertEquals(fullArtist.id, domain.id)
        assertEquals(fullArtist.name, domain.name)
        assertEquals(fullArtist.imageUrl, domain.imageUrl)
        assertEquals(fullArtist.genres, domain.genres)
        assertEquals(fullArtist.popularity, domain.popularity)
        assertEquals(fullArtist.songCount, domain.songCount)
    }

    @Test
    fun fromDomain_mapsAllFields() {
        val entity = ArtistEntity.fromDomain(fullArtist)

        assertEquals("a1", entity.id)
        assertEquals("Test Artist", entity.name)
        assertEquals("https://img/a1", entity.imageUrl)
        assertEquals(listOf("rock", "pop"), entity.genres)
        assertEquals(85, entity.popularity)
        assertEquals(12, entity.songCount)
    }

    @Test
    fun roundTrip_equalsSameFields() {
        val entity = ArtistEntity.fromDomain(fullArtist)
        val restored = entity.toDomain()
        assertEquals(fullArtist, restored)
    }

    @Test
    fun multipleGenres_preservesOrder() {
        val genres = listOf("jazz", "blues", "rock", "electronic")
        val artist = fullArtist.copy(genres = genres)
        val restored = ArtistEntity.fromDomain(artist).toDomain()
        assertEquals(genres, restored.genres)
    }
}
