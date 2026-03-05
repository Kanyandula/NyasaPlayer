package com.example.nyasaplayer.data.local.entity

import com.example.nyasaplayer.models.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class SongEntityTest {

    private val fullSong = Song(
        mediaId = "42",
        title = "Test Song",
        subtitle = "Test Subtitle",
        imageUrl = "https://img/42",
        songUrl = "https://song/42",
        artistId = "a1",
        artistName = "Artist One",
        albumId = "alb1",
        albumName = "Album One",
        durationMs = 180_000L,
        genreIds = listOf("g1", "g2"),
        coverUrl = "https://cover/42",
        audioUrl = "https://audio/42",
        popularity = 95,
        isExplicit = true,
    )

    @Test
    fun toDomain_mapsAllFields() {
        val entity = SongEntity.fromDomain(fullSong)
        val domain = entity.toDomain()

        assertEquals(fullSong.mediaId, domain.mediaId)
        assertEquals(fullSong.title, domain.title)
        assertEquals(fullSong.subtitle, domain.subtitle)
        assertEquals(fullSong.imageUrl, domain.imageUrl)
        assertEquals(fullSong.songUrl, domain.songUrl)
        assertEquals(fullSong.artistId, domain.artistId)
        assertEquals(fullSong.artistName, domain.artistName)
        assertEquals(fullSong.albumId, domain.albumId)
        assertEquals(fullSong.albumName, domain.albumName)
        assertEquals(fullSong.durationMs, domain.durationMs)
        assertEquals(fullSong.genreIds, domain.genreIds)
        assertEquals(fullSong.coverUrl, domain.coverUrl)
        assertEquals(fullSong.audioUrl, domain.audioUrl)
        assertEquals(fullSong.popularity, domain.popularity)
        assertEquals(fullSong.isExplicit, domain.isExplicit)
    }

    @Test
    fun fromDomain_mapsAllFields() {
        val entity = SongEntity.fromDomain(fullSong)

        assertEquals("42", entity.mediaId)
        assertEquals("Test Song", entity.title)
        assertEquals("Test Subtitle", entity.subtitle)
        assertEquals("https://img/42", entity.imageUrl)
        assertEquals("https://song/42", entity.songUrl)
        assertEquals("a1", entity.artistId)
        assertEquals("Artist One", entity.artistName)
        assertEquals("alb1", entity.albumId)
        assertEquals("Album One", entity.albumName)
        assertEquals(180_000L, entity.durationMs)
        assertEquals(listOf("g1", "g2"), entity.genreIds)
        assertEquals("https://cover/42", entity.coverUrl)
        assertEquals("https://audio/42", entity.audioUrl)
        assertEquals(95, entity.popularity)
        assertEquals(true, entity.isExplicit)
    }

    @Test
    fun roundTrip_equalsSameFields() {
        val entity = SongEntity.fromDomain(fullSong)
        val restored = entity.toDomain()
        assertEquals(fullSong, restored)
    }

    @Test
    fun emptyGenreIds_roundTripsCorrectly() {
        val song = fullSong.copy(genreIds = emptyList())
        val restored = SongEntity.fromDomain(song).toDomain()
        assertEquals(emptyList<String>(), restored.genreIds)
    }

    @Test
    fun multipleGenreIds_preservesOrder() {
        val ids = listOf("z", "a", "m", "b")
        val song = fullSong.copy(genreIds = ids)
        val restored = SongEntity.fromDomain(song).toDomain()
        assertEquals(ids, restored.genreIds)
    }
}
