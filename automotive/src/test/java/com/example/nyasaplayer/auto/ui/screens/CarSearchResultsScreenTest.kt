package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.nyasaplayer.auto.search.AutomotiveSearchResult
import com.example.nyasaplayer.auto.search.AutomotiveSearchResults
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Screen 6 — the sectioned result list. */
@RunWith(RobolectricTestRunner::class)
class CarSearchResultsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clicked = mutableListOf<AutomotiveSearchResult>()

    @Test
    fun `every populated section is labelled and empty ones are left out`() {
        render(everyType())

        // Scrolled to, not asserted in place: four sections are taller than a head unit, and a
        // lazy list has not composed what is still below the fold.
        listOf("Songs", "Albums", "Artists", "Playlists").forEach { section ->
            scrollTo(section)
            composeRule.onNodeWithText(section).assertIsDisplayed()
        }
    }

    @Test
    fun `a type with no results has no header`() {
        render(everyType().copy(artists = emptyList(), playlists = emptyList()))

        composeRule.onNodeWithText("Songs").assertIsDisplayed()
        composeRule.onNodeWithText("Artists").assertDoesNotExist()
        composeRule.onNodeWithText("Playlists").assertDoesNotExist()
    }

    /** The coordinator drops the featured result from its section; the screen must not re-add it. */
    @Test
    fun `the featured card is the only place its title appears`() {
        render(everyType())

        composeRule.onNodeWithText("Top result").assertIsDisplayed()
        composeRule.onNodeWithText("Featured Song").assertIsDisplayed()
    }

    @Test
    fun `each card hands back its own typed result`() {
        render(everyType())

        listOf("Section Song", "Grace Sessions", "Grace Choir", "Grace Drive").forEach { label ->
            scrollTo(label)
            composeRule.onNodeWithText(label).performClick()
        }

        assertEquals(
            listOf("song:s1", "album:al1", "artist:ar1", "playlist:p1"),
            clicked.map { it.stableId },
        )
    }

    @Test
    fun `the featured card reports the featured result`() {
        render(everyType())

        composeRule.onNodeWithText("Featured Song").performClick()

        assertEquals(listOf("song:featured"), clicked.map { it.stableId })
    }

    @Test
    fun `no results offers a way back to the query`() {
        render(AutomotiveSearchResults(query = "grace"))

        composeRule.onNodeWithText("No results").assertIsDisplayed()
        composeRule.onNodeWithText("Edit search").assertIsDisplayed()
    }

    /**
     * A list taller than the screen must actually scroll.
     *
     * It did not: the list filled the *whole* screen height rather than what was left under the
     * header, so it overflowed the bottom, believed everything fit, and left every section below
     * the fold unreachable on device. Scrolling to the last section has to move the featured card
     * off screen.
     */
    @Test
    fun `a list taller than the screen scrolls instead of overflowing it`() {
        render(
            everyType().copy(
                songs = List(12) { index ->
                    AutomotiveSearchResult.SongResult(
                        Song(mediaId = "s$index", title = "Section Song $index"),
                    )
                },
            ),
        )

        composeRule.onNodeWithText("Top result").assertIsDisplayed()
        scrollTo("Playlists")

        composeRule.onNodeWithText("Playlists").assertIsDisplayed()
        composeRule.onNodeWithText("Top result").assertIsNotDisplayed()
    }

    private fun scrollTo(text: String) {
        composeRule.onNodeWithTag(SearchResultsListTag).performScrollToNode(hasText(text))
    }

    private fun render(results: AutomotiveSearchResults) {
        composeRule.setContent {
            CarSearchResultsScreen(
                query = "grace",
                results = results,
                isLoading = false,
                errorMessage = null,
                onBackToSearch = {},
                onClear = {},
                onRetry = {},
                onResultClick = { clicked += it },
            )
        }
    }

    private fun everyType() = AutomotiveSearchResults(
        query = "grace",
        featured = AutomotiveSearchResult.SongResult(Song(mediaId = "featured", title = "Featured Song")),
        songs = listOf(
            AutomotiveSearchResult.SongResult(Song(mediaId = "s1", title = "Section Song")),
        ),
        albums = listOf(
            AutomotiveSearchResult.AlbumResult(Album(id = "al1", name = "Grace Sessions")),
        ),
        artists = listOf(
            AutomotiveSearchResult.ArtistResult(Artist(id = "ar1", name = "Grace Choir")),
        ),
        playlists = listOf(
            AutomotiveSearchResult.PlaylistResult(Playlist(id = "p1", name = "Grace Drive")),
        ),
    )
}
