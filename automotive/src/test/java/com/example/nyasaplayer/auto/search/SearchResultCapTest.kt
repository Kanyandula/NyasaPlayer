package com.example.nyasaplayer.auto.search

import com.example.nyasaplayer.auto.viewmodel.UxRestrictionState
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The driving cap counts cards, not sections: four sections each capped at
 * `maxCumulativeContentItems` would render four times what the platform allowed (spec 3.6).
 */
class SearchResultCapTest {

    @Test
    fun `parked results are returned untouched`() {
        val results = results(songCount = 8, albumCount = 4)

        assertSame(results, results.capped(UxRestrictionState()))
    }

    @Test
    fun `driving caps the whole rendered stream, featured card included`() {
        val results = results(songCount = 8, albumCount = 4)

        val capped = results.capped(driving(maxItems = 4))

        // featured + three songs: the albums sit below the songs in render order and are gone.
        assertNotNull(capped.featured)
        assertEquals(3, capped.songs.size)
        assertEquals(emptyList<String>(), capped.albums.map { it.album.id })
    }

    @Test
    fun `a cap that reaches into a lower section keeps the sections above it whole`() {
        val results = results(songCount = 2, albumCount = 4)

        val capped = results.capped(driving(maxItems = 5))

        assertNotNull(capped.featured)
        assertEquals(2, capped.songs.size)
        assertEquals(listOf("al0", "al1"), capped.albums.map { it.album.id })
    }

    @Test
    fun `a cap of zero leaves nothing to render`() {
        val capped = results(songCount = 3, albumCount = 1).capped(driving(maxItems = 0))

        assertNull(capped.featured)
        assertEquals(true, capped.isEmpty)
    }

    private fun driving(maxItems: Int) = UxRestrictionState(
        requiresDistractionOptimization = true,
        maxCumulativeContentItems = maxItems,
    )

    private fun results(songCount: Int, albumCount: Int) = AutomotiveSearchResults(
        query = "grace",
        featured = songResult("featured"),
        songs = List(songCount) { songResult("s$it") },
        albums = List(albumCount) { index ->
            AutomotiveSearchResult.AlbumResult(
                album = Album(id = "al$index", name = "Album $index"),
                rank = SearchRank(2, 1, 0, "album $index"),
            )
        },
    )

    private fun songResult(id: String) = AutomotiveSearchResult.SongResult(
        song = Song(mediaId = id, title = "Song $id"),
        rank = SearchRank(1, 0, 0, "song $id"),
    )
}
