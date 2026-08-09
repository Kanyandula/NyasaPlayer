package com.example.nyasaplayer.auto.viewmodel

import com.example.nyasaplayer.auto.MainDispatcherRule
import com.example.nyasaplayer.auto.fake.FakeAlbumRepository
import com.example.nyasaplayer.auto.fake.FakeAuthRepository
import com.example.nyasaplayer.auto.fake.FakeGenreRepository
import com.example.nyasaplayer.auto.fake.FakePlaylistRepository
import com.example.nyasaplayer.auto.fake.FakeSongRepository
import com.example.nyasaplayer.auto.fake.FakeUserRepository
import com.example.nyasaplayer.auto.ui.navigation.CarDestination
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.models.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailLoadingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songs = FakeSongRepository()
    private val albums = FakeAlbumRepository()
    private val playlists = FakePlaylistRepository()

    private fun viewModel() = AutomotiveContentViewModel(
        songRepository = songs,
        genreRepository = FakeGenreRepository(),
        albumRepository = albums,
        userRepository = FakeUserRepository(),
        authRepository = FakeAuthRepository(),
        playlistRepository = playlists,
    )

    private fun song(id: String) = Song(
        mediaId = id,
        title = "Title $id",
        artistName = "Artist $id",
        durationMs = 1000L,
    )

    private fun album(id: String, songIds: List<String>) = Album(
        id = id,
        name = "Album $id",
        artistName = "Artist of $id",
        imageUrl = "https://example.test/$id.jpg",
        songIds = songIds,
    )

    private fun playlist(id: String, songIds: List<String>) = Playlist(
        id = id,
        name = "Playlist $id",
        songIds = songIds,
    )

    @Test
    fun newViewModel_hasNoDetail() = runTest {
        val vm = viewModel()
        assertNull(vm.contentState.value.detail)
        assertTrue(vm.contentState.value.playlists.isEmpty())
    }

    @Test
    fun openDetail_album_populatesTracksInSongIdOrder() = runTest {
        songs.songs.value = listOf(song("c"), song("a"), song("b"))
        albums.albums.value = listOf(album("al1", listOf("b", "a", "c")))
        val vm = viewModel()

        vm.openDetail(CarDestination.Album("al1"))

        val detail = requireNotNull(vm.contentState.value.detail)
        assertEquals(listOf("b", "a", "c"), detail.tracks.map { it.mediaId })
        assertEquals("Album al1", detail.title)
        assertFalse(detail.isLoading)
    }

    @Test
    fun openDetail_playlist_populatesTracksInSongIdOrder() = runTest {
        songs.songs.value = listOf(song("x"), song("y"))
        val vm = viewModel()
        playlists.emit(listOf(playlist("pl1", listOf("y", "x"))))

        vm.openDetail(CarDestination.Playlist("pl1"))

        val detail = requireNotNull(vm.contentState.value.detail)
        assertEquals(listOf("y", "x"), detail.tracks.map { it.mediaId })
        assertEquals("Playlist pl1", detail.title)
    }

    @Test
    fun openDetail_secondCallInFlight_firstResultIsDiscarded() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        albums.albums.value = listOf(
            album("first", listOf("a")),
            album("second", listOf("b")),
        )
        val vm = viewModel()
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()

        songs.gate = firstGate
        vm.openDetail(CarDestination.Album("first"))
        songs.gate = secondGate
        vm.openDetail(CarDestination.Album("second"))

        // Two gates so the FIRST load finishes LAST. The first job is cancelled but resumes
        // non-cancellably, exactly like a Firestore callback that fires anyway, and tries to
        // write its stale result over the second's. Only the guard in openDetail stops it.
        secondGate.complete(Unit)
        advanceUntilIdle()
        firstGate.complete(Unit)
        advanceUntilIdle()

        val detail = requireNotNull(vm.contentState.value.detail)
        assertEquals(CarDestination.Album("second"), detail.destination)
        assertEquals(listOf("b"), detail.tracks.map { it.mediaId })
    }

    @Test
    fun closeDetail_inFlightLoadDoesNotRepopulate() = runTest {
        songs.songs.value = listOf(song("a"))
        albums.albums.value = listOf(album("al1", listOf("a")))
        val gate = CompletableDeferred<Unit>()
        songs.gate = gate
        val vm = viewModel()

        vm.openDetail(CarDestination.Album("al1"))
        vm.closeDetail()
        songs.gate = null
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(vm.contentState.value.detail)
    }

    @Test
    fun openDetail_staleLoadOfSameAlbum_doesNotOverwriteFresherResult() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        albums.albums.value = listOf(album("al1", listOf("a")))
        val vm = viewModel()
        val staleGate = CompletableDeferred<Unit>()

        songs.gate = staleGate
        vm.openDetail(CarDestination.Album("al1"))
        vm.closeDetail()

        // The same album, reopened once the data caught up. Destination equality cannot tell the
        // two loads apart, so only the token stops the parked first one from winning.
        albums.albums.value = listOf(album("al1", listOf("a", "b")))
        songs.gate = null
        vm.openDetail(CarDestination.Album("al1"))
        advanceUntilIdle()

        staleGate.complete(Unit)
        advanceUntilIdle()

        val detail = requireNotNull(vm.contentState.value.detail)
        assertEquals(listOf("a", "b"), detail.tracks.map { it.mediaId })
    }

    @Test
    fun openDetail_artist_leavesDetailNull() = runTest {
        val vm = viewModel()

        vm.openDetail(CarDestination.Artist("ar1", "Artist One"))

        assertNull(vm.contentState.value.detail)
    }

    @Test
    fun openDetail_albumWithNoResolvableTracks_settlesEmptyNotLoading() = runTest {
        albums.albums.value = listOf(album("al1", listOf("missing")))
        val vm = viewModel()

        vm.openDetail(CarDestination.Album("al1"))

        val detail = requireNotNull(vm.contentState.value.detail)
        assertTrue(detail.tracks.isEmpty())
        assertFalse(detail.isLoading)
        assertNull(detail.errorMessage)
    }

    @Test
    fun openDetail_playlistBeforeFirstEmission_resolvesWhenItArrives() = runTest {
        songs.songs.value = listOf(song("x"))
        val vm = viewModel()

        // No emit() yet — this is the process-death restore path (D17).
        vm.openDetail(CarDestination.Playlist("pl1"))
        assertTrue(requireNotNull(vm.contentState.value.detail).isLoading)

        playlists.emit(listOf(playlist("pl1", listOf("x"))))
        advanceUntilIdle()

        val detail = requireNotNull(vm.contentState.value.detail)
        assertFalse(detail.isLoading)
        assertEquals(listOf("x"), detail.tracks.map { it.mediaId })
    }

    @Test
    fun openDetail_playlistAbsentFromArrivedEmission_setsError() = runTest {
        val vm = viewModel()
        playlists.emit(listOf(playlist("other", emptyList())))

        vm.openDetail(CarDestination.Playlist("pl1"))
        advanceUntilIdle()

        val detail = requireNotNull(vm.contentState.value.detail)
        assertFalse(detail.isLoading)
        assertNotNull(detail.errorMessage)
    }

    @Test
    fun openDetail_unknownAlbum_setsError() = runTest {
        val vm = viewModel()

        vm.openDetail(CarDestination.Album("nope"))
        advanceUntilIdle()

        val detail = requireNotNull(vm.contentState.value.detail)
        assertFalse(detail.isLoading)
        assertNotNull(detail.errorMessage)
    }
}
