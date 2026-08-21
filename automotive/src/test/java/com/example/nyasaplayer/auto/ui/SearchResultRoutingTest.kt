package com.example.nyasaplayer.auto.ui

import com.example.nyasaplayer.auto.search.AutomotiveSearchResult
import com.example.nyasaplayer.auto.ui.navigation.CarDestination
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where each kind of result card goes when the driver taps it (spec 3.6). */
class SearchResultRoutingTest {

    private var played: Pair<List<Song>, Song>? = null
    private var opened: CarDestination? = null

    @Test
    fun `a song plays from the visible queue`() {
        val queue = listOf(song("a"), song("b"))

        route(AutomotiveSearchResult.SongResult(song("b")), songQueue = queue)

        assertEquals(queue to song("b"), played)
        assertNull(opened)
    }

    @Test
    fun `an album opens album detail`() {
        route(AutomotiveSearchResult.AlbumResult(Album(id = "al1")))

        assertEquals(CarDestination.Album("al1"), opened)
        assertNull(played)
    }

    /** The liked-songs artist screen is a different destination and must not be reused here. */
    @Test
    fun `an artist opens catalogue artist detail`() {
        route(AutomotiveSearchResult.ArtistResult(Artist(id = "ar1", name = "Grace")))

        assertEquals(CarDestination.CatalogArtist("ar1"), opened)
    }

    @Test
    fun `a playlist opens playlist detail`() {
        route(AutomotiveSearchResult.PlaylistResult(Playlist(id = "p1")))

        assertEquals(CarDestination.Playlist("p1"), opened)
    }

    private fun route(
        result: AutomotiveSearchResult,
        songQueue: List<Song> = emptyList(),
    ) = routeSearchResult(
        result = result,
        songQueue = songQueue,
        onPlay = { queue, song -> played = queue to song },
        onOpenDetail = { opened = it },
    )

    private fun song(id: String) = Song(mediaId = id, title = "Song $id")
}
