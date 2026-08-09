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

@OptIn(ExperimentalCoroutinesApi::class)
class FavouritesSnapshotTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songs = FakeSongRepository()
    private val users = FakeUserRepository()

    private fun viewModel() = AutomotiveContentViewModel(
        songRepository = songs,
        genreRepository = FakeGenreRepository(),
        albumRepository = FakeAlbumRepository(),
        playlistRepository = FakePlaylistRepository(),
        userRepository = users,
        authRepository = FakeAuthRepository(),
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

        vm.toggleFavourite("a")
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
        vm.toggleFavourite("a")
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
        vm.toggleFavourite("a")
        advanceUntilIdle()

        vm.toggleFavourite("a")
        advanceUntilIdle()

        assertFalse("a" in vm.contentState.value.pendingUnlikes)
        assertEquals(listOf("a", "b"), vm.contentState.value.favourites?.map { it.mediaId })
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
        vm.toggleFavourite("a")
        advanceUntilIdle()
        users.liked.value = listOf(likedSong("b"), likedSong("c"))
        advanceUntilIdle()

        vm.toggleFavourite("b")
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
        vm.toggleFavourite("a")
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
        vm.toggleFavourite("a")
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

        val ok = vm.toggleFavourite("a")
        advanceUntilIdle()

        assertFalse(ok)
        assertFalse("a" in vm.contentState.value.pendingUnlikes)
    }
}
