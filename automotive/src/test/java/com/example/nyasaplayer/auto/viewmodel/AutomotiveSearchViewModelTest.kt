package com.example.nyasaplayer.auto.viewmodel

import com.example.nyasaplayer.auto.MainDispatcherRule
import com.example.nyasaplayer.auto.fake.FakeAlbumRepository
import com.example.nyasaplayer.auto.fake.FakeArtistRepository
import com.example.nyasaplayer.auto.fake.FakeAuthRepository
import com.example.nyasaplayer.auto.fake.FakePlaylistRepository
import com.example.nyasaplayer.auto.fake.FakeSongRepository
import com.example.nyasaplayer.auto.search.AutomotiveCatalogSearch
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomotiveSearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songs = FakeSongRepository()
    private val albums = FakeAlbumRepository()
    private val artists = FakeArtistRepository()
    private val playlists = FakePlaylistRepository()

    // The real coordinator over fake repositories: these tests are about the ViewModel's
    // state machine, and stubbing the ranking would stop proving that typed results reach state.
    private fun viewModel() = AutomotiveSearchViewModel(
        catalogSearch = AutomotiveCatalogSearch(
            songRepository = songs,
            albumRepository = albums,
            artistRepository = artists,
            playlistRepository = playlists,
            authRepository = FakeAuthRepository(),
        ),
    )

    @Test
    fun `results keep their type on the way into state`() = runTest {
        songs.songs.value = listOf(song("Worship Medley"))
        albums.albums.value = listOf(Album(id = "al1", name = "Worship Nights"))
        val vm = viewModel()
        vm.onQueryChange("worship")

        vm.submitSearch()

        assertEquals(listOf("Worship Medley"), vm.uiState.value.results.songQueue.map { it.title })
        assertEquals(listOf("al1"), vm.uiState.value.results.albums.map { it.album.id })
    }

    @Test
    fun `a stale search cannot deliver its non-song sections either`() = runTest {
        songs.songs.value = listOf(song("Slow Song", album = "slow"), song("Fast Song", album = "fast"))
        albums.albums.value = listOf(Album(id = "slow-album", name = "Slow Nights"))
        val slow = CompletableDeferred<Unit>()
        songs.searchGates["slow"] = slow
        val vm = viewModel()

        vm.onQueryChange("slow")
        vm.submitSearch()
        vm.onQueryChange("fast")
        vm.submitSearch()
        slow.complete(Unit)

        assertEquals("fast", vm.uiState.value.submittedQuery)
        assertEquals(emptyList<String>(), vm.uiState.value.results.albums.map { it.album.id })
    }

    private fun song(title: String, artist: String = "Artist", album: String = "Album") =
        Song(mediaId = title, title = title, artistName = artist, albumName = album)

    @Test
    fun `typing does not search`() = runTest {
        val vm = viewModel()

        vm.onQueryChange("wor")

        assertEquals("wor", vm.uiState.value.query)
        assertTrue(songs.searchQueries.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `blank submit does not call the repository`() = runTest {
        val vm = viewModel()
        vm.onQueryChange("   ")

        vm.submitSearch()

        assertTrue(songs.searchQueries.isEmpty())
        assertTrue(vm.uiState.value.results.isEmpty)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `submit trims the query and records it as submitted`() = runTest {
        songs.songs.value = listOf(song("Worship Medley"))
        val vm = viewModel()
        vm.onQueryChange("  worship  ")

        vm.submitSearch()

        assertEquals(listOf("worship"), songs.searchQueries)
        assertEquals("worship", vm.uiState.value.submittedQuery)
    }

    @Test
    fun `successful submit populates results and records the recent query`() = runTest {
        songs.songs.value = listOf(song("Worship Medley"), song("Something Else"))
        val vm = viewModel()
        vm.onQueryChange("worship")

        vm.submitSearch()

        assertEquals(listOf("Worship Medley"), vm.uiState.value.results.songQueue.map { it.title })
        assertEquals(listOf("worship"), vm.uiState.value.recentQueries)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `re-submitting a query moves it to the top instead of duplicating it`() = runTest {
        val vm = viewModel()

        vm.onQueryChange("alpha")
        vm.submitSearch()
        vm.onQueryChange("beta")
        vm.submitSearch()
        vm.onQueryChange("ALPHA")
        vm.submitSearch()

        assertEquals(listOf("ALPHA", "beta"), vm.uiState.value.recentQueries)
    }

    @Test
    fun `recent queries cap at five, newest first`() = runTest {
        val vm = viewModel()

        listOf("one", "two", "three", "four", "five", "six").forEach {
            vm.onQueryChange(it)
            vm.submitSearch()
        }

        assertEquals(listOf("six", "five", "four", "three", "two"), vm.uiState.value.recentQueries)
    }

    @Test
    fun `a failed search reports an error and keeps the query available for retry`() = runTest {
        songs.searchFailure = IllegalStateException("offline")
        val vm = viewModel()
        vm.onQueryChange("worship")

        vm.submitSearch()

        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("worship", vm.uiState.value.submittedQuery)
    }

    @Test
    fun `retry re-runs the last submitted query`() = runTest {
        songs.searchFailure = IllegalStateException("offline")
        val vm = viewModel()
        vm.onQueryChange("worship")
        vm.submitSearch()
        songs.searchFailure = null
        songs.songs.value = listOf(song("Worship Medley"))

        vm.retrySearch()

        assertEquals(listOf("worship", "worship"), songs.searchQueries)
        assertEquals(listOf("Worship Medley"), vm.uiState.value.results.songQueue.map { it.title })
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `a stale result cannot overwrite a newer search`() = runTest {
        songs.songs.value = listOf(song("Slow Song", album = "slow"), song("Fast Song", album = "fast"))
        val slow = CompletableDeferred<Unit>()
        songs.searchGates["slow"] = slow
        val vm = viewModel()

        vm.onQueryChange("slow")
        vm.submitSearch()
        vm.onQueryChange("fast")
        vm.submitSearch()
        slow.complete(Unit)

        assertEquals("fast", vm.uiState.value.submittedQuery)
        assertEquals(listOf("Fast Song"), vm.uiState.value.results.songQueue.map { it.title })
    }

    @Test
    fun `clearing cancels in-flight work and empties query, results and error`() = runTest {
        songs.songs.value = listOf(song("Worship Medley"))
        val gate = CompletableDeferred<Unit>()
        songs.searchGates["worship"] = gate
        val vm = viewModel()
        vm.onQueryChange("worship")
        vm.submitSearch()

        vm.clearQuery()
        gate.complete(Unit)

        assertEquals("", vm.uiState.value.query)
        assertEquals("", vm.uiState.value.submittedQuery)
        assertTrue(vm.uiState.value.results.isEmpty)
        assertNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `clearing keeps the session's recent queries`() = runTest {
        val vm = viewModel()
        vm.onQueryChange("worship")
        vm.submitSearch()

        vm.clearQuery()

        assertEquals(listOf("worship"), vm.uiState.value.recentQueries)
    }

    @Test
    fun `tapping a recent query searches it and fills the field`() = runTest {
        songs.songs.value = listOf(song("Worship Medley"))
        val vm = viewModel()

        vm.selectRecentQuery("worship")

        assertEquals("worship", vm.uiState.value.query)
        assertEquals(listOf("worship"), songs.searchQueries)
        assertEquals(listOf("Worship Medley"), vm.uiState.value.results.songQueue.map { it.title })
    }

    @Test
    fun `a new submit drops the previous query's results rather than showing them under it`() =
        runTest {
            songs.songs.value = listOf(song("Worship Medley"), song("Banjo Song"))
            val gate = CompletableDeferred<Unit>()
            val vm = viewModel()
            vm.onQueryChange("worship")
            vm.submitSearch()
            assertEquals(listOf("Worship Medley"), vm.uiState.value.results.songQueue.map { it.title })

            songs.searchGates["banjo"] = gate
            vm.onQueryChange("banjo")
            vm.submitSearch()

            assertTrue(vm.uiState.value.results.isEmpty)
            assertTrue(vm.uiState.value.isLoading)
            gate.complete(Unit)
            assertEquals(listOf("Banjo Song"), vm.uiState.value.results.songQueue.map { it.title })
        }

    @Test
    fun `a failed search shows no rows under the query that failed`() = runTest {
        songs.songs.value = listOf(song("Worship Medley"))
        val vm = viewModel()
        vm.onQueryChange("worship")
        vm.submitSearch()

        songs.searchFailure = IllegalStateException("offline")
        vm.onQueryChange("banjo")
        vm.submitSearch()

        assertNotNull(vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.results.isEmpty)
    }

    @Test
    fun `back keeps the draft query but closes results`() = runTest {
        songs.songs.value = listOf(song("Worship Medley"))
        val vm = viewModel()
        vm.onQueryChange("worship")
        vm.submitSearch()

        vm.backToSearch()

        assertEquals("worship", vm.uiState.value.query)
        assertEquals("", vm.uiState.value.submittedQuery)
        assertTrue(vm.uiState.value.results.isEmpty)
    }

    @Test
    fun `back keeps the session's recent queries`() = runTest {
        val vm = viewModel()
        vm.onQueryChange("worship")
        vm.submitSearch()

        vm.backToSearch()

        assertEquals(listOf("worship"), vm.uiState.value.recentQueries)
    }

    @Test
    fun `back cancels an in-flight search so it cannot reopen results`() = runTest {
        songs.songs.value = listOf(song("Worship Medley"))
        val gate = CompletableDeferred<Unit>()
        songs.searchGates["worship"] = gate
        val vm = viewModel()
        vm.onQueryChange("worship")
        vm.submitSearch()

        vm.backToSearch()
        gate.complete(Unit)

        assertEquals("", vm.uiState.value.submittedQuery)
        assertTrue(vm.uiState.value.results.isEmpty)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `back leaves a failed search recoverable from the field`() = runTest {
        songs.searchFailure = IllegalStateException("offline")
        val vm = viewModel()
        vm.onQueryChange("worship")
        vm.submitSearch()

        vm.backToSearch()

        assertNull(vm.uiState.value.errorMessage)
        assertEquals("worship", vm.uiState.value.query)
    }

    @Test
    fun `submitting closes the editor so results are not read as text entry`() = runTest {
        val vm = viewModel()
        vm.setEditing(true)
        vm.onQueryChange("worship")

        vm.submitSearch()

        assertFalse(vm.uiState.value.isEditing)
    }

    @Test
    fun `a blank submit leaves the editor open`() = runTest {
        val vm = viewModel()
        vm.setEditing(true)

        vm.onQueryChange("   ")
        vm.submitSearch()

        assertTrue(vm.uiState.value.isEditing)
    }

    @Test
    fun `editing state is explicit, not derived from the query text`() = runTest {
        val vm = viewModel()

        vm.onQueryChange("worship")
        assertFalse(vm.uiState.value.isEditing)

        vm.setEditing(true)
        assertTrue(vm.uiState.value.isEditing)

        vm.setEditing(false)
        assertFalse(vm.uiState.value.isEditing)
    }
}
