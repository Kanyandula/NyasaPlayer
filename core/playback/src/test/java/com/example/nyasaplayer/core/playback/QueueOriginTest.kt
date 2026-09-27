package com.example.nyasaplayer.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/** T01: the origin survives its primitive form, and anything unreadable becomes [QueueOrigin.None]. */
class QueueOriginTest {

    private fun roundTrip(origin: QueueOrigin): QueueOrigin =
        origin.toFields().let { (kind, id, name) -> queueOriginOf(kind, id, name) }

    @Test
    fun every_origin_round_trips() {
        listOf(
            QueueOrigin.Playlist(id = "p1", name = "Road Trip"),
            QueueOrigin.Album(id = "al1", name = "Kalindula"),
            QueueOrigin.Artist(id = "ar1", name = "Lucius Banda"),
            QueueOrigin.Genre(name = "Afrobeat"),
            QueueOrigin.Search(query = "banda"),
            QueueOrigin.Favourites,
            QueueOrigin.Downloads,
            QueueOrigin.RecentlyPlayed,
            QueueOrigin.None,
        ).forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun missing_origin_is_none() {
        // States saved before origins existed carry blank fields; Bundles without the keys, nulls.
        assertEquals(QueueOrigin.None, queueOriginOf("", "", ""))
        assertEquals(QueueOrigin.None, queueOriginOf(null, null, null))
    }

    @Test
    fun unknown_kind_is_none() {
        assertEquals(QueueOrigin.None, queueOriginOf("podcast", "x", "Y"))
        // Kinds are exact strings, not class names.
        assertEquals(QueueOrigin.None, queueOriginOf("Playlist", "p1", "Road Trip"))
    }

    @Test
    fun a_blank_field_the_kind_needs_is_none() {
        assertEquals(QueueOrigin.None, queueOriginOf("playlist", "", "Road Trip"))
        assertEquals(QueueOrigin.None, queueOriginOf("album", "al1", " "))
        assertEquals(QueueOrigin.None, queueOriginOf("artist", null, "Lucius Banda"))
        assertEquals(QueueOrigin.None, queueOriginOf("genre", "", ""))
        assertEquals(QueueOrigin.None, queueOriginOf("search", "", null))
    }

    @Test
    fun fields_a_kind_ignores_do_not_matter() {
        assertEquals(QueueOrigin.Favourites, queueOriginOf("favourites", "stray", "stray"))
        assertEquals(QueueOrigin.Genre("Afrobeat"), queueOriginOf("genre", "stray", "Afrobeat"))
    }
}
