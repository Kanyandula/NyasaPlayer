package com.example.nyasaplayer.auto.search

import com.example.nyasaplayer.auto.fake.FakeAlbumRepository
import com.example.nyasaplayer.auto.fake.FakeArtistRepository
import com.example.nyasaplayer.auto.fake.FakeAuthRepository
import com.example.nyasaplayer.auto.fake.FakePlaylistRepository
import com.example.nyasaplayer.auto.fake.FakeSongRepository
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.models.Song
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomotiveCatalogSearchTest {

    private val songs = FakeSongRepository()
    private val albums = FakeAlbumRepository()
    private val artists = FakeArtistRepository()
    private val playlists = FakePlaylistRepository()
    private val auth = FakeAuthRepository()

    private val search = AutomotiveCatalogSearch(songs, albums, artists, playlists, auth)

    @Test
    fun `one query returns every type that matches`() = runTest {
        songs.songs.value = listOf(song(id = "s1", title = "Grace Like Rain"))
        albums.albums.value = listOf(album(id = "al1", name = "Grace Sessions"))
        artists.artists.value = listOf(artist(id = "ar1", name = "Grace Choir"))
        playlists.emit(listOf(playlist(id = "p1", name = "Grace Drive")))

        val results = search.search("grace")

        // The song is the featured card, so it is deliberately absent from the songs section.
        assertEquals("song:s1", results.featured?.stableId)
        assertEquals(
            setOf("song:s1", "album:al1", "artist:ar1", "playlist:p1"),
            results.everyCard().map { it.stableId }.toSet(),
        )
        assertEquals("grace", results.query)
    }

    @Test
    fun `an exact album match outranks a song that only matches on its album field`() = runTest {
        songs.songs.value = listOf(song(id = "s1", title = "Another Song", albumName = "Grace"))
        albums.albums.value = listOf(album(id = "al1", name = "Grace"))

        val results = search.search("grace")

        assertEquals("album:al1", results.featured?.stableId)
    }

    @Test
    fun `the featured result is not repeated in its own section`() = runTest {
        songs.songs.value = listOf(
            song(id = "top", title = "Grace", popularity = 90),
            song(id = "other", title = "Grace Abounds", popularity = 10),
        )

        val results = search.search("grace")

        assertEquals("song:top", results.featured?.stableId)
        assertEquals(listOf("other"), results.songs.map { it.song.mediaId })
    }

    @Test
    fun `equal matches fall back to popularity then title then id`() = runTest {
        songs.songs.value = listOf(
            song(id = "b", title = "Grace Rising", popularity = 5),
            song(id = "a", title = "Grace Rising", popularity = 5),
            song(id = "loud", title = "Grace Falling", popularity = 50),
        )

        val results = search.search("grace")

        assertEquals("song:loud", results.featured?.stableId)
        assertEquals(listOf("a", "b"), results.songs.map { it.song.mediaId })
    }

    @Test
    fun `signed out searches the catalogue and returns no playlists`() = runTest {
        auth.currentUserId = null
        songs.songs.value = listOf(song(id = "s1", title = "Grace"))
        playlists.emit(listOf(playlist(id = "p1", name = "Grace Drive")))

        val results = search.search("grace")

        assertEquals("song:s1", results.featured?.stableId)
        assertEquals(emptyList<String>(), results.playlists.map { it.playlist.id })
    }

    @Test
    fun `a failing repository fails the whole search rather than returning part of it`() = runTest {
        songs.songs.value = listOf(song(id = "s1", title = "Grace"))
        artists.searchFailure = IllegalStateException("artists offline")

        val thrown = runCatching { search.search("grace") }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun `a blank query searches nothing`() = runTest {
        songs.songs.value = listOf(song(id = "s1", title = "Grace"))

        val results = search.search("   ")

        assertTrue(results.isEmpty)
        assertNull(results.featured)
        assertEquals(emptyList<String>(), songs.searchQueries)
    }

    @Test
    fun `the committed query is trimmed once, for every type`() = runTest {
        songs.songs.value = listOf(song(id = "s1", title = "Grace"))

        val results = search.search("  grace  ")

        assertEquals("grace", results.query)
        assertEquals(listOf("grace"), songs.searchQueries)
    }

    private fun AutomotiveSearchResults.everyCard(): List<AutomotiveSearchResult> =
        listOfNotNull(featured) + songs + albums + artists + playlists

    private fun song(
        id: String,
        title: String,
        albumName: String = "",
        popularity: Int = 0,
    ) = Song(mediaId = id, title = title, albumName = albumName, popularity = popularity)

    private fun album(id: String, name: String, popularity: Int = 0) =
        Album(id = id, name = name, popularity = popularity)

    private fun artist(id: String, name: String, popularity: Int = 0) =
        Artist(id = id, name = name, popularity = popularity)

    private fun playlist(id: String, name: String) = Playlist(id = id, name = name)
}
