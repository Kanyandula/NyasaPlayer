package com.example.nyasaplayer.auto.ui

import com.example.nyasaplayer.auto.viewmodel.UxRestrictionState
import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The artist drill-down is the one capped list that filters first, so the order of the two
 * operations is real logic rather than plumbing.
 */
class ArtistLikedSongsTest {

    private val liked = List(30) { index ->
        Song(
            mediaId = "$index",
            title = "Song $index",
            // Every third song is the artist under test: 10 of the 30.
            artistId = if (index % 3 == 0) "wanted" else "other",
        )
    }

    private val driving = UxRestrictionState(
        requiresDistractionOptimization = true,
        maxCumulativeContentItems = 5,
    )

    @Test
    fun `the cap applies to the artist's songs, not to the library it filtered them from`() {
        val songs = artistLikedSongs(liked, "wanted", driving)

        // Capping first would take 5 of the 30 liked songs and leave only 2 by this artist.
        assertEquals(5, songs.size)
        assertEquals(listOf("wanted"), songs.map { it.artistId }.distinct())
    }

    @Test
    fun `parked shows every song by the artist`() {
        val parked = UxRestrictionState(
            requiresDistractionOptimization = false,
            maxCumulativeContentItems = 5,
        )

        assertEquals(10, artistLikedSongs(liked, "wanted", parked).size)
    }

    @Test
    fun `an artist with nothing liked yields nothing`() {
        assertEquals(emptyList<Song>(), artistLikedSongs(liked, "nobody", driving))
    }
}
