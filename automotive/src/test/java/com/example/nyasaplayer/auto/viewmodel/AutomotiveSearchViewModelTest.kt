package com.example.nyasaplayer.auto.viewmodel

import com.example.nyasaplayer.auto.MainDispatcherRule
import com.example.nyasaplayer.auto.fake.FakeSongRepository
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

    private fun viewModel() = AutomotiveSearchViewModel(songRepository = songs)

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
        assertTrue(vm.uiState.value.results.isEmpty())
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

        assertEquals(listOf("Worship Medley"), vm.uiState.value.results.map { it.title })
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
        assertEquals(listOf("Worship Medley"), vm.uiState.value.results.map { it.title })
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
        assertEquals(listOf("Fast Song"), vm.uiState.value.results.map { it.title })
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
        assertTrue(vm.uiState.value.results.isEmpty())
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
        assertEquals(listOf("Worship Medley"), vm.uiState.value.results.map { it.title })
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
