package com.example.nyasaplayer.auto.viewmodel

import com.example.nyasaplayer.auto.MainDispatcherRule
import com.example.nyasaplayer.auto.fake.FakeAlbumRepository
import com.example.nyasaplayer.auto.fake.FakeAuthRepository
import com.example.nyasaplayer.auto.fake.FakeGenreRepository
import com.example.nyasaplayer.auto.fake.FakePlaylistRepository
import com.example.nyasaplayer.auto.fake.FakeSongRepository
import com.example.nyasaplayer.auto.fake.FakeUserRepository
import com.example.nyasaplayer.core.common.models.LikedSong
import com.example.nyasaplayer.core.common.models.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Release gate: whole driver journeys across the fourteen fixes, not per-row assertions.
 *
 * Every step here is already covered in isolation by `FavouritesBoundaryTest` and
 * `FavouritesSnapshotTest`. What these add is the *composition* — the rows were fixed one at a
 * time and several of them touch the same three functions, so this checks the sequences a driver
 * actually performs still hold end to end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavouritesJourneyTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songs = FakeSongRepository()
    private val users = FakeUserRepository()
    private val auth = FakeAuthRepository()
    private val genres = FakeGenreRepository()

    private fun viewModel() = AutomotiveContentViewModel(
        songRepository = songs,
        genreRepository = genres,
        albumRepository = FakeAlbumRepository(),
        playlistRepository = FakePlaylistRepository(),
        userRepository = users,
        authRepository = auth,
    )

    private fun song(id: String) = Song(mediaId = id, title = "Title $id", artistName = "Artist $id")
    private fun likedSong(id: String) = LikedSong(mediaId = id, likedAt = 1L)
    private fun AutomotiveContentState.rendered() = (favourites ?: likedSongs).map { it.mediaId }

    /** Enter Favourites, unlike, leave, return. Rows #1, #2, #19-freeze, #20-guard. */
    @Test
    fun journey_unlikeHoldsTheRowUntilTheVisitEnds() = runTest {
        songs.songs.value = listOf(song("a"), song("b"), song("c"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"), likedSong("c"))
        advanceUntilIdle()

        vm.openFavourites()
        vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()

        // Held: the row keeps its place with a hollow heart while the server drops it.
        users.liked.value = listOf(likedSong("b"), likedSong("c"))
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), vm.contentState.value.rendered())
        assertTrue("a" in vm.contentState.value.pendingUnlikes)

        // Re-entering the tab mid-visit must not reconcile (D20).
        vm.openFavourites()
        assertEquals(listOf("a", "b", "c"), vm.contentState.value.rendered())

        // Leaving ends the visit; returning shows the reconciled list.
        vm.closeFavourites()
        vm.openFavourites()
        assertEquals(listOf("b", "c"), vm.contentState.value.rendered())
        assertEquals(emptySet<String>(), vm.contentState.value.pendingUnlikes)
    }

    /** Fail a load, see the error, retry, recover. Rows #3, #7, #10, #12, #14. */
    @Test
    fun journey_failedLoadShowsAnErrorAndRetryRecovers() = runTest {
        songs.songs.value = listOf(song("a"))
        genres.genresError = IllegalStateException("catalogue down")
        users.likedSongsFlowError = IllegalStateException("liked songs down")
        val vm = viewModel()
        advanceUntilIdle()

        // Each failure lands on its own channel; screen 8 shows its own, not the catalogue's.
        assertNotNull(vm.contentState.value.favouritesError)
        assertNotNull(vm.contentState.value.errorMessage)

        genres.genresError = null
        users.likedSongsFlowError = null
        users.liked.value = listOf(likedSong("a"))
        vm.retryLoad()
        advanceUntilIdle()

        assertNull(vm.contentState.value.favouritesError)
        assertNull(vm.contentState.value.errorMessage)
        assertEquals(listOf("a"), vm.contentState.value.rendered())
    }

    /** Switch accounts mid-visit with a freeze held. Rows #6, #12, #14. */
    @Test
    fun journey_accountSwitchMidVisitLeavesNothingOfTheOldUser() = runTest {
        songs.songs.value = listOf(song("a"), song("b"), song("z"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()
        vm.openFavourites()
        vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()

        users.likedFor("other-user").value = listOf(likedSong("z"))
        auth.currentUserId = "other-user"
        vm.reloadUserContent()
        advanceUntilIdle()

        val state = vm.contentState.value
        assertNull("A's freeze must not follow B", state.favourites)
        assertEquals(emptySet<String>(), state.pendingUnlikes)
        assertNull("nor A's liked-songs error", state.favouritesError)
        assertEquals(listOf("z"), state.rendered())

        // And the held emission from A's visit must not land on B when the tab closes.
        vm.closeFavourites()
        assertEquals(listOf("z"), vm.contentState.value.rendered())
    }
}
