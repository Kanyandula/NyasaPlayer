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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class FavouritesSnapshotTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songs = FakeSongRepository()
    private val users = FakeUserRepository()
    private val auth = FakeAuthRepository()

    private fun viewModel() = AutomotiveContentViewModel(
        songRepository = songs,
        genreRepository = FakeGenreRepository(),
        albumRepository = FakeAlbumRepository(),
        playlistRepository = FakePlaylistRepository(),
        userRepository = users,
        authRepository = auth,
    )

    private fun song(id: String) = Song(mediaId = id, title = "Title $id", artistName = "Artist $id")

    private fun likedSong(id: String) = LikedSong(mediaId = id, likedAt = 1L)

    @Test
    fun openFavourites_aloneDoesNotFreeze() = runTest {
        val vm = viewModel()
        vm.openFavourites()
        assertNull(vm.contentState.value.favourites)
    }

    @Test
    fun likedSongsArrivingAfterOpen_isReflected() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()
        vm.openFavourites()
        assertNull(vm.contentState.value.favourites)

        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.contentState.value.likedSongs.map { it.mediaId })
        assertNull(vm.contentState.value.favourites)
    }

    @Test
    fun firstUnlike_freezesThePreRemovalList() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()
        vm.openFavourites()

        vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.contentState.value.favourites?.map { it.mediaId })
        assertTrue("a" in vm.contentState.value.pendingUnlikes)
    }

    @Test
    fun liveFlowDroppingAnUnlikedSong_doesNotChangeTheFreeze() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()
        vm.openFavourites()
        vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()

        users.liked.value = listOf(likedSong("b"))
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.contentState.value.favourites?.map { it.mediaId })
    }

    @Test
    fun reLiking_clearsPendingWithoutChangingTheFreeze() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()
        vm.openFavourites()
        vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()

        vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()

        assertFalse("a" in vm.contentState.value.pendingUnlikes)
        assertEquals(listOf("a", "b"), vm.contentState.value.favourites?.map { it.mediaId })
        // Pins *which* write happened. Without these, swapping the branch so a re-like sends a
        // second unlikeSong leaves every other assertion green while the server drops the song.
        assertEquals(1, users.unlikeCallCount)
        assertEquals(1, users.likeCallCount)
    }

    /**
     * Added beyond the brief's eight. Every one of those stayed green with the `?:` in
     * `favourites = state.favourites ?: state.likedSongs` replaced by a plain
     * `state.likedSongs` — none of them unlikes a second time *after* the live flow has moved,
     * which is the only way a re-freeze is observable. Without this the never-re-freeze half of
     * the guard is deletable with no test going red.
     */
    @Test
    fun secondUnlikeAfterLiveFlowMoved_doesNotRecaptureTheFreeze() = runTest {
        songs.songs.value = listOf(song("a"), song("b"), song("c"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"), likedSong("c"))
        advanceUntilIdle()
        vm.openFavourites()
        vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()
        users.liked.value = listOf(likedSong("b"), likedSong("c"))
        advanceUntilIdle()

        vm.toggleFavourite("b", freeze = true)
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), vm.contentState.value.favourites?.map { it.mediaId })
        assertTrue("a" in vm.contentState.value.pendingUnlikes)
        assertTrue("b" in vm.contentState.value.pendingUnlikes)
    }

    @Test
    fun closeThenOpen_reconciles() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()
        vm.openFavourites()
        vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()
        users.liked.value = listOf(likedSong("b"))
        advanceUntilIdle()

        vm.closeFavourites()
        vm.openFavourites()

        assertNull(vm.contentState.value.favourites)
        assertTrue(vm.contentState.value.pendingUnlikes.isEmpty())
        assertEquals(listOf("b"), vm.contentState.value.likedSongs.map { it.mediaId })
    }

    @Test
    fun repeatOpenWhileFrozen_preservesFreezeAndPending() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()
        vm.openFavourites()
        vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()

        vm.openFavourites()

        assertEquals(listOf("a", "b"), vm.contentState.value.favourites?.map { it.mediaId })
        assertTrue("a" in vm.contentState.value.pendingUnlikes)
    }

    @Test
    fun failedUnlike_revertsPendingAndReportsFailure() = runTest {
        songs.songs.value = listOf(song("a"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"))
        advanceUntilIdle()
        vm.openFavourites()
        users.failNextWrite = true

        val ok = vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()

        assertFalse(ok)
        assertFalse("a" in vm.contentState.value.pendingUnlikes)
        assertEquals(1, users.unlikeCallCount)
        // Records the intended behaviour, which was previously unpinned in both directions: the
        // freeze survives a failed write. The row goes back to solid, but the list must still
        // not reflow under the driver's finger for the rest of the visit.
        assertEquals(listOf("a"), vm.contentState.value.favourites?.map { it.mediaId })
    }

    @Test
    fun failedReLike_restoresThePendingUnlike() = runTest {
        songs.songs.value = listOf(song("a"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"))
        advanceUntilIdle()
        vm.openFavourites()
        vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()

        // The other direction of the revert. Collapsing it to an unconditional `- mediaId`
        // survives failedUnlike_… but loses the pending unlike here, leaving the heart solid
        // for a song the server still has unliked.
        users.failNextWrite = true
        val ok = vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()

        assertFalse(ok)
        assertTrue("a" in vm.contentState.value.pendingUnlikes)
    }

    @Test
    fun cancelledWrite_propagatesInsteadOfReportingFailure() = runTest {
        songs.songs.value = listOf(song("a"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"))
        advanceUntilIdle()
        vm.openFavourites()
        users.throwOnNextWrite = CancellationException("collector cancelled")

        val thrown = runCatching { vm.toggleFavourite("a", freeze = true) }.exceptionOrNull()

        // Without the CancellationException rethrow the generic catch swallows it and returns
        // false, and the caller raises a spurious "Couldn't Save" for a write that probably
        // landed — the screen was simply torn down while it was in flight.
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun unfrozenUnlike_pendsAndWritesWithoutTakingAFreeze() = runTest {
        // The artist liked-songs drill-down (spec D25): a live list that removes rows
        // immediately. A freeze taken there would leak into the next Favourites visit, because
        // closeFavourites() is driven by a tab change and the drill-down stays on Library.
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()

        val ok = vm.toggleFavourite("a", freeze = false)
        advanceUntilIdle()

        assertTrue(ok)
        assertNull(vm.contentState.value.favourites)
        assertTrue("a" in vm.contentState.value.pendingUnlikes)
        assertEquals(1, users.unlikeCallCount)
    }

    @Test
    fun userSwitch_clearsTheFreezeAndPendingUnlikes() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()
        vm.openFavourites()
        vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()

        // Sign out, sign a different account in on the same head unit. Without the clear in
        // reloadUserContent(), user B sees user A's frozen list and A's hollow heart.
        auth.currentUserId = "other-user"
        vm.reloadUserContent()
        advanceUntilIdle()

        assertNull(vm.contentState.value.favourites)
        assertTrue(vm.contentState.value.pendingUnlikes.isEmpty())
    }
}
