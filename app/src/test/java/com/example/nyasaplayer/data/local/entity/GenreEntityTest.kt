package com.example.nyasaplayer.data.local.entity

import com.example.nyasaplayer.models.Genre
import org.junit.Assert.assertEquals
import org.junit.Test

class GenreEntityTest {

    private val fullGenre = Genre(
        id = "g1",
        name = "Rock",
        color = "#FF0000",
        imageUrl = "https://img/g1",
        popularity = 90,
        songIds = listOf("s1", "s2"),
    )

    @Test
    fun toDomain_mapsAllFields() {
        val entity = GenreEntity.fromDomain(fullGenre)
        val domain = entity.toDomain()

        assertEquals(fullGenre.id, domain.id)
        assertEquals(fullGenre.name, domain.name)
        assertEquals(fullGenre.color, domain.color)
        assertEquals(fullGenre.imageUrl, domain.imageUrl)
        assertEquals(fullGenre.popularity, domain.popularity)
        assertEquals(fullGenre.songIds, domain.songIds)
    }

    @Test
    fun fromDomain_mapsAllFields() {
        val entity = GenreEntity.fromDomain(fullGenre)

        assertEquals("g1", entity.id)
        assertEquals("Rock", entity.name)
        assertEquals("#FF0000", entity.color)
        assertEquals("https://img/g1", entity.imageUrl)
        assertEquals(90, entity.popularity)
        assertEquals(listOf("s1", "s2"), entity.songIds)
    }

    @Test
    fun roundTrip_equalsSameFields() {
        val entity = GenreEntity.fromDomain(fullGenre)
        val restored = entity.toDomain()
        assertEquals(fullGenre, restored)
    }

    @Test
    fun multipleSongIds_preservesOrder() {
        val ids = listOf("z", "a", "m", "b", "c")
        val genre = fullGenre.copy(songIds = ids)
        val restored = GenreEntity.fromDomain(genre).toDomain()
        assertEquals(ids, restored.songIds)
    }
}
