package com.example.nyasaplayer.auto.viewmodel

import com.example.nyasaplayer.auto.MainDispatcherRule
import com.example.nyasaplayer.auto.fake.FakeAlbumRepository
import com.example.nyasaplayer.auto.fake.FakeAuthRepository
import com.example.nyasaplayer.auto.fake.FakeGenreRepository
import com.example.nyasaplayer.auto.fake.FakePlaylistRepository
import com.example.nyasaplayer.auto.fake.FakeSongRepository
import com.example.nyasaplayer.auto.fake.FakeUserRepository
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.LikedSong
import com.example.nyasaplayer.core.common.models.Song
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

/**
 * The boundary of the freeze model: writers of liked state that are **not** `toggleFavourite`,
 * and readers of it that are never reconciled.
 *
 * `FavouritesSnapshotTest` covers the inside of the model and every one of its behaviours is
 * mutation-verified. These cover the outside, which nothing did — see ledger rows #1, #2 and #3.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavouritesBoundaryTest {

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

    /**
     * Exactly what screen 8 draws, mirroring AutomotiveApp.kt:465. Asserting on `likedSongs`
     * alone would prove nothing about the screen; asserting on `favourites` alone would skip
     * the case this file is about, which is the one where no freeze was ever taken.
     */
    private fun AutomotiveContentState.rendered(): List<String> =
        (favourites ?: likedSongs).map { it.mediaId }

    /**
     * Row #1, removal half. Screen 8 defers row removal until refresh. The deferral is
     * implemented entirely inside `toggleFavourite`, so an unlike performed anywhere else —
     * the mini player heart, which BrowseShell draws on every tab, or the phone, since the
     * Firestore listener is live — drops the row immediately and reflows the list under the
     * driver's finger.
     */
    @Test
    fun unlikeFromAnotherWriter_mustNotRemoveTheRowWhileOnFavourites() = runTest {
        songs.songs.value = listOf(song("a"), song("b"), song("c"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"), likedSong("c"))
        advanceUntilIdle()
        vm.openFavourites()

        // No unlike on this screen, so no freeze is taken and the live list is what renders.
        // Another writer unlikes b; the snapshot listener re-emits without it.
        users.liked.value = listOf(likedSong("a"), likedSong("c"))
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), vm.contentState.value.rendered())
    }

    /**
     * Row #1, insertion half. `likedSongs` is ordered `likedAt DESCENDING`
     * (FirebaseUserRepository.kt:51) and `likeSong` stamps `Timestamp.now()` (:66), so a like
     * from any other writer lands at index 0 and shifts every row down one row height. The
     * hero `item {}` at scroll position 0 is un-keyed, so LazyColumn's key-based anchoring
     * does not hold the list still.
     */
    @Test
    fun likeFromAnotherWriter_mustNotShiftTheRowsWhileOnFavourites() = runTest {
        songs.songs.value = listOf(song("a"), song("b"), song("z"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()
        vm.openFavourites()

        users.liked.value = listOf(likedSong("z"), likedSong("a"), likedSong("b"))
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.contentState.value.rendered())
    }

    /**
     * Row #2. `pendingUnlikes` is one set with two writers on two tabs and one lifecycle hook,
     * the tab-keyed effect at AutomotiveApp.kt:154. Leaving the artist drill-down sets
     * `drillDown = null` without changing `currentScreen`, so the effect does not re-run and
     * nothing clears the pend. `openFavourites()` deliberately clears nothing (D20), so the
     * pend arrives on screen 8 and paints a song that *is* liked with a hollow heart —
     * `isLiked = mediaId !in pendingUnlikes`, CarFavouriteMusicScreen.kt:107.
     *
     * Closed by clearing pends in `openFavourites()` *guarded on there being no freeze*. That is
     * not D20's clear, which protects a freeze and the pends behind it: on screen 8 a pend always
     * implies a freeze (`freeze = true` at that call site), so the guarded clear can only ever
     * drop a drill-down leftover. `repeatOpenWhileFrozen_preservesFreezeAndPending` still holds
     * the D20 side, and goes red if the guard is dropped.
     */
    @Test
    fun pendFromTheDrillDown_mustNotSurviveIntoTheNextFavouritesVisit() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()

        // On Library, inside artist X: the live drill-down unlikes a and the row leaves (D25).
        vm.toggleFavourite("a", freeze = false)
        advanceUntilIdle()

        // The server drops a, then a is re-liked from the mini player or the phone; neither
        // touches pendingUnlikes. Both emissions must be distinct values — a StateFlow conflates
        // a reassignment of an equal value, and an earlier version of this test set [a, b] twice
        // and so never drove an emission at all.
        users.liked.value = listOf(likedSong("b"))
        advanceUntilIdle()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()

        // Back out of the drill-down (currentScreen unchanged, no close fires), then tap the
        // Liked songs card, which is the only thing that runs the Favourites branch.
        vm.openFavourites()

        // a is liked, so it must not be pending: the heart on screen 8 reads this set directly.
        assertEquals(listOf("a", "b"), vm.contentState.value.rendered())
        assertEquals(emptySet<String>(), vm.contentState.value.pendingUnlikes)
    }

    /**
     * Row #3. Both liked-songs catches set `likedSongsLoaded = true` and neither sets
     * `errorMessage`, so AutomotiveApp.kt:480-481 hands screen 8 `isLoading = false`,
     * `errorMessage = null` and an empty list — and CarFavouriteMusicScreen.kt:81 falls to the
     * empty branch. A driver whose liked songs failed to load is told they have none, and
     * offered a CTA off the tab. Spec §4.1 puts this in the error branch with a retry.
     */
    @Test
    fun failedLikedSongsLoad_reportsAnErrorRatherThanAnEmptyLibrary() = runTest {
        users.likedSongsFlowError = IllegalStateException("PERMISSION_DENIED")
        val vm = viewModel()
        advanceUntilIdle()

        // Pins that the catch actually ran, so a green first assertion cannot come from a flow
        // that never started: the load is over, and it produced nothing.
        assertTrue("expected the failure path to settle the load", vm.contentState.value.likedSongsLoaded)
        assertEquals(emptyList<String>(), vm.contentState.value.rendered())

        // The load is over — the skeleton must not strand — but "over" is not "empty".
        assertNotNull(
            "screen 8 renders its empty state whenever its error is null and songs is empty",
            vm.contentState.value.favouritesError,
        )
    }

    /**
     * Row #3, secondary. `loadContent()` resets `isLoading` and `errorMessage` but not
     * `likedSongsLoaded`, so Retry leaves Favourites on the false empty state instead of
     * returning it to the skeleton while the reload is in flight.
     */
    @Test
    fun retryAfterAFailedLikedSongsLoad_returnsFavouritesToTheSkeleton() = runTest {
        users.likedSongsFlowError = IllegalStateException("PERMISSION_DENIED")
        val vm = viewModel()
        advanceUntilIdle()

        // The retry succeeds, but the flag from the failed attempt is never cleared, so the
        // screen never goes back to loading.
        users.likedSongsFlowError = null
        songs.gate = kotlinx.coroutines.CompletableDeferred()
        vm.retryLoad()

        assertFalse(
            "retryLoad() leaves likedSongsLoaded true, so Favourites keeps the false empty state",
            vm.contentState.value.likedSongsLoaded,
        )
        // Row #10: the skeleton is only reachable if the error is cleared too — the error branch
        // is tested first (CarFavouriteMusicScreen.kt:71), so a retry that resets the flag but
        // leaves the message still renders the error it was retried out of.
        assertNull(
            "retryLoad() must clear the error Favourites reads, not just the loaded flag",
            vm.contentState.value.favouritesError,
        )
    }

    /**
     * The error value screen 8 actually renders from, mirroring what `AutomotiveApp.kt` passes
     * into `CarFavouriteMusicScreen`. Kept as one helper because that binding is about to move
     * from the shared `errorMessage` to a dedicated field — re-point this and both error rows
     * keep asserting on what the screen reads, rather than on a field it no longer looks at.
     */
    // ponytail: no local helper — these assert on AutomotiveContentState.favouritesError /
    // .favouritesLoading, the same derivations AutomotiveApp binds into the screen. A hand-mirror
    // here is what let mutation Q3 survive: two copies of one decision drift silently.

    /**
     * Row #8, the mirror of #7. `errorMessage` has three producers — `observeGenres` (:147),
     * `observeAlbums` (:156) and `reportLikedSongsFailure` — and screen 8 reads it directly.
     * A genres failure therefore paints "Something went wrong" over the empty state, because
     * `CarFavouriteMusicScreen.kt:71` tests `errorMessage != null && songs.isEmpty()` before the
     * empty branch at :81. Whether the genres catalogue loaded has nothing to do with whether
     * the driver has favourites, and D21 wants screen 17 reachable whenever the library is empty.
     */
    @Test
    fun genresFailure_mustNotPaintAnErrorOverTheEmptyFavourites() = runTest {
        genres.genresError = IllegalStateException("Room genres failure")
        val vm = viewModel()
        users.liked.value = emptyList()
        advanceUntilIdle()

        // The liked-songs load itself succeeded and is genuinely empty: screen 17's state.
        assertTrue(vm.contentState.value.likedSongsLoaded)
        assertEquals(emptyList<String>(), vm.contentState.value.rendered())

        assertNull(
            "a genres failure must not reach screen 8; it outranks CarEmptyFavouritesScreen",
            vm.contentState.value.favouritesError,
        )
    }

    /**
     * Row #15. `loadContent()` clears four flags on behalf of six collectors, but three of the
     * six abort on a null user id (`?: return`), so a Retry taken in that state leaves the
     * user-scoped flows dead with their flags reset — and having also cleared both error fields,
     * it removes the Retry affordance that got the driver here. Row #5 fixed this shape in
     * `reloadUserContent` (`?: return` at :137); `loadContent` was never audited for it.
     */
    @Test
    fun retryWithNoUserId_mustNotStrandFavouritesOnTheSkeleton() = runTest {
        songs.songs.value = listOf(song("a"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"))
        advanceUntilIdle()

        auth.currentUserId = null
        vm.retryLoad()
        advanceUntilIdle()

        songs.songs.value = listOf(song("a"), song("b"))
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()

        val state = vm.contentState.value
        assertFalse(
            "Retry left the liked-songs collector dead with its flags reset and no error, so " +
                "screen 8 renders FavouritesSkeleton forever and no Retry is reachable",
            state.favouritesLoading && state.favouritesError == null,
        )
        // The emission landing is the proof the collector survived; the flags are not. A
        // flags-only guard would pass the assertion above while leaving three dead collectors
        // and a screen showing a list nothing maintains.
        assertEquals(listOf("a", "b"), state.likedSongs.map { it.mediaId })
    }

    /**
     * Row #17. Firestore's listener fires from cache then server, so with an empty local cache
     * the FIRST callback is an empty snapshot. If the driver is already on Favourites, that
     * emission publishes (the hold needs `likedSongsLoaded` already true, and it is not), setting
     * `likedSongsLoaded = true` and an empty list — and the *server* snapshot carrying the real
     * library then satisfies the hold and is withheld for the rest of the visit. Screen 8 shows
     * "No favourites yet" plus a CTA off the tab to a driver who has liked songs: the false empty
     * state §4.1 rules out, reintroduced one layer up by row #1's fix.
     */
    @Test
    fun coldStartEmptyCacheThenServer_mustNotWithholdTheRealLibrary() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()
        vm.openFavourites()

        // Emission 1: the cached, empty snapshot.
        users.liked.value = emptyList()
        advanceUntilIdle()

        // Emission 2: the server snapshot with the driver's actual library.
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()

        assertEquals(
            "the server snapshot is held behind an empty cached one, so screen 8 offers " +
                "'No favourites yet' to a driver who has favourites",
            listOf("a", "b"),
            vm.contentState.value.rendered(),
        )
    }

    /**
     * Row #15, the other half. The user-scoped teardown is guarded, but the catalogue half must
     * stay unconditional: genres and albums need no user id, and after #14 they own
     * `errorMessage`, so a Retry with no user is still the only thing that can retire and rebuild
     * a catalogue failure. Guarding both halves would pass every other test.
     */
    @Test
    fun retryWithNoUserId_stillRebuildsTheCatalogue() = runTest {
        genres.genresError = IllegalStateException("Room genres failure")
        val vm = viewModel()
        advanceUntilIdle()
        assertNotNull(vm.contentState.value.errorMessage)

        auth.currentUserId = null
        genres.genresError = null
        genres.genres.value = listOf(Genre(id = "g1", name = "Gospel"))
        vm.retryLoad()
        advanceUntilIdle()

        assertNull("a Retry without a user must still retire the catalogue error", vm.contentState.value.errorMessage)
        assertEquals(listOf("g1"), vm.contentState.value.genres.map { it.id })
    }

    /**
     * Row #14, the other direction. A user switch must not clear the catalogue error, but a
     * Retry must — `retryLoad()` restarts genres and albums, so it is the one event that is
     * evidence the failure may be over. Same rule from the recovery side.
     */
    @Test
    fun retryLoad_clearsALiveCatalogueFailure() = runTest {
        genres.genresError = IllegalStateException("Room genres failure")
        val vm = viewModel()
        advanceUntilIdle()
        assertNotNull(vm.contentState.value.errorMessage)

        genres.genresError = null
        vm.retryLoad()
        advanceUntilIdle()

        assertNull("retryLoad restarts genres, so it must retire their error", vm.contentState.value.errorMessage)
    }

    /**
     * Row #13, adjudication. The claim is that `observeGenres`'s success path never clears
     * `errorMessage`, so a genres failure followed by a genres success leaves the message set.
     * The second half of that sequence is unreachable: `.catch` is a terminal operator, so the
     * collector is gone after the failure and no later emission is delivered — only `retryLoad()`
     * restarts it, and that clears the field. This pins the reason the missing retire is
     * harmless, so that if anyone converts genres to an inner catch (as liked songs has, which
     * is exactly what made rows #7 and #12 reachable) this test goes red and the retire becomes
     * genuinely required.
     */
    @Test
    fun genresFailure_terminatesItsFlow_soNoSuccessCanFollow() = runTest {
        genres.genresError = IllegalStateException("Room genres failure")
        val vm = viewModel()
        advanceUntilIdle()
        assertNotNull(vm.contentState.value.errorMessage)

        // The failure is over; push a good genres emission at the same flow.
        genres.genresError = null
        genres.genres.value = listOf(Genre(id = "g1", name = "Gospel"))
        advanceUntilIdle()

        assertEquals(
            "if this arrives, the collector survived its error and the missing retire is a real defect",
            emptyList<String>(),
            vm.contentState.value.genres.map { it.id },
        )
    }

    /**
     * Row #12, trigger 1. `reportLikedSongsFailure` writes the liked-songs message into the
     * shared `errorMessage` as well, and only the dedicated copy is ever retired — so after a
     * recovery screen 8 is clean while Home, Browse and Library still hold a liked-songs error
     * they have no way to clear. On a first run, before the Firestore→Room sync lands, Browse's
     * first branch is `errorMessage != null && genres.isEmpty()`, so it renders that error ahead
     * of its own skeleton for a Browse screen that is working.
     */
    @Test
    fun recoveredLikedSongsLoad_retiresTheSharedErrorToo() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()

        songs.throwOnceOnGetSongsByIds = IllegalStateException("transient Room failure")
        users.liked.value = listOf(likedSong("a"))
        advanceUntilIdle()
        // The failure is recorded on the dedicated channel only — asserting it on the shared
        // field here is what the fix removed, and an earlier version of this test did exactly
        // that, so it failed under every mutation rather than discriminating between them.
        assertNotNull(vm.contentState.value.favouritesError)
        assertNull("a liked-songs failure must not reach the shared field at all", vm.contentState.value.errorMessage)

        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()

        assertNull(
            "the liked-songs message outlives its cause in the field Home/Browse/Library read",
            vm.contentState.value.errorMessage,
        )
    }

    /**
     * Row #12, trigger 2. `loadContent()` clears both error fields; `reloadUserContent()` clears
     * neither. So user A's liked-songs failure survives into user B's session, and because screen
     * 8 tests its error branch before its loading branch, B sees A's failure instead of the
     * skeleton for the whole first Firestore round trip.
     */
    @Test
    fun userSwitch_clearsBothErrorFields() = runTest {
        songs.songs.value = listOf(song("a"))
        val vm = viewModel()
        songs.throwOnceOnGetSongsByIds = IllegalStateException("transient Room failure")
        users.liked.value = listOf(likedSong("a"))
        advanceUntilIdle()
        assertNotNull(vm.contentState.value.favouritesError)

        auth.currentUserId = "other-user"
        vm.reloadUserContent()
        advanceUntilIdle()

        assertNull("user A's failure must not attach to user B", vm.contentState.value.favouritesError)
        // ponytail: only the liked-songs field. The shared one is row #14 — a catalogue failure
        // is the process's, not the account's, and a user switch is not evidence it recovered.
    }

    /**
     * Row #14. `reloadUserContent` clears the shared `errorMessage`, but its only writers are
     * genres and albums, which are catalogue-wide and are NOT restarted by a user switch — and
     * whose flows are already dead, per `genresFailure_terminatesItsFlow_soNoSuccessCanFollow`.
     * So clearing it leaves B with no genres, no error, and no Retry — the affordance lives on
     * the error UI — and nothing will ever re-raise it. The failure is still true for B.
     */
    @Test
    fun userSwitch_mustNotClearALiveCatalogueFailure() = runTest {
        genres.genresError = IllegalStateException("Room genres failure")
        val vm = viewModel()
        advanceUntilIdle()
        assertNotNull(vm.contentState.value.errorMessage)

        auth.currentUserId = "other-user"
        vm.reloadUserContent()
        advanceUntilIdle()

        assertNotNull(
            "the genres flow is dead and nothing restarts it on a user switch; clearing the " +
                "message strands B with a broken catalogue and no Retry",
            vm.contentState.value.errorMessage,
        )
    }

    /**
     * Row #9, raised by qa from a surviving mutation. Row #7's fix retires the shared
     * `errorMessage` only when it *matches* the liked-songs message. Replacing that value-match
     * with an unconditional `errorMessage = null` left all 66 tests green, so the discriminating
     * half of the fix was undefended — and it is the half that stops a liked-songs success from
     * wiping a failure that genres or albums reported. Same shape as row #6.
     */
    @Test
    fun likedSongsSuccess_mustNotWipeAForeignError() = runTest {
        songs.songs.value = listOf(song("a"))
        genres.genresError = IllegalStateException("Room genres failure")
        val vm = viewModel()
        advanceUntilIdle()
        assertNotNull(vm.contentState.value.errorMessage)

        users.liked.value = listOf(likedSong("a"))
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.contentState.value.likedSongs.map { it.mediaId })
        assertNotNull(
            "a liked-songs success retires its own error and only its own; genres and albums " +
                "write the same field and their failure must survive",
            vm.contentState.value.errorMessage,
        )
    }

    /**
     * Row #7. `publishLikedSongs` sets `likedSongs`/`favoriteArtists`/`likedSongsLoaded` and never
     * clears `errorMessage`, and it is the single point every successful load routes through —
     * so an error retires nowhere. Once a transient failure has recovered invisibly (songs are
     * non-empty, so screen 8 skips the error branch), the stale value outranks screen 17 the next
     * time the library is genuinely empty: `CarFavouriteMusicScreen.kt:71` tests `errorMessage`
     * before `songs.isEmpty()`. D21 requires screen 17 to stay reachable.
     */
    @Test
    fun recoveredLikedSongsLoad_retiresTheError() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()

        songs.throwOnceOnGetSongsByIds = IllegalStateException("transient Room failure")
        users.liked.value = listOf(likedSong("a"))
        advanceUntilIdle()
        // Row #3's fix, working: the failure is on the error channel, not the empty state.
        assertNotNull(vm.contentState.value.favouritesError)

        // The inner catch left the collector alive, so the next emission resolves normally.
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.contentState.value.likedSongs.map { it.mediaId })
        assertNull(
            "a recovered load must retire the error it recovered from, or the stale value " +
                "outranks CarEmptyFavouritesScreen the next time the library is empty",
            vm.contentState.value.favouritesError,
        )
    }

    /**
     * Row #6, raised by qa from a surviving mutation, not by red.
     *
     * `reloadUserContent()` clears `heldLikedSongs`. Deleting that line left all 63 tests green,
     * so nothing defended it — the same shape as the A3 guard that was removed unnoticed.
     *
     * It is load-bearing: the hold survives a user switch otherwise. A is on Favourites when an
     * emission is held; the account switches; B's own liked songs arrive and publish normally
     * because `likedSongsLoaded` was just reset; then B leaves the tab and `closeFavourites()`
     * applies A's held emission over B's list. A sees nothing wrong — B sees A's liked songs.
     */
    @Test
    fun heldEmissionFromAPreviousAccount_mustNotLandOnTheNextUser() = runTest {
        songs.songs.value = listOf(song("a"), song("b"), song("z"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()
        vm.openFavourites()

        // Mid-visit emission for user A: held, not applied.
        users.liked.value = listOf(likedSong("a"))
        advanceUntilIdle()

        // Sign a different account in on the same head unit, still on the tab. B's own liked
        // songs must come from B's collection — driving the same flow lets B's collector
        // re-emit A's data and overwrite the stale hold, which hides the very leak under test.
        users.likedFor("other-user").value = listOf(likedSong("z"))
        auth.currentUserId = "other-user"
        vm.reloadUserContent()
        advanceUntilIdle()

        vm.closeFavourites()

        assertEquals(listOf("z"), vm.contentState.value.likedSongs.map { it.mediaId })
    }

    /**
     * Row #5. `reloadUserContent()`'s guard at :105 only protects the same-user case. A null
     * `currentUserId` is not equal to the old one, so it falls through, cancels all three
     * user-scoped collectors and clears their state — and then the three restarts each hit
     * their own `?: return` and no-op. Nothing recovers it short of process death.
     *
     * Null is reachable while the shell is still composed because `isAuthenticated` is a
     * snapshot seeded once at AutomotiveAuthViewModel.kt:35-37 with no `AuthStateListener`
     * anywhere in the module, so Firebase invalidating the session server-side does not take
     * the app off `AuthenticatedApp`. `AutomotiveActivity` declares no `configChanges`, so a
     * night-mode flip rebuilds the composition and re-runs `LaunchedEffect(Unit)`.
     */
    @Test
    fun nullUserIdOnRecreation_mustNotStrandFavouritesOnTheSkeleton() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()

        auth.currentUserId = null
        vm.reloadUserContent()
        advanceUntilIdle()

        // The collector is gone, so a later emission cannot rescue the screen either.
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()

        val state = vm.contentState.value
        assertFalse(
            "Favourites is stranded: !likedSongsLoaded renders FavouritesSkeleton " +
                "(CarFavouriteMusicScreen.kt:79) and a null error means no Retry is reachable",
            state.favouritesLoading && state.favouritesError == null,
        )
    }

    /**
     * Row #5, D20 half. The same fall-through clears `favourites` and `pendingUnlikes` at
     * :118-119. A night-mode flip is D20's own worked example, so the freeze is destroyed
     * mid-drive by the one path the decision exists to rule out.
     */
    @Test
    fun nullUserIdOnRecreation_mustNotDestroyTheFreeze() = runTest {
        songs.songs.value = listOf(song("a"), song("b"))
        val vm = viewModel()
        users.liked.value = listOf(likedSong("a"), likedSong("b"))
        advanceUntilIdle()
        vm.openFavourites()
        vm.toggleFavourite("a", freeze = true)
        advanceUntilIdle()

        auth.currentUserId = null
        vm.reloadUserContent()
        advanceUntilIdle()

        val state = vm.contentState.value
        assertEquals(listOf("a", "b"), state.favourites?.map { it.mediaId })
        assertTrue("a" in state.pendingUnlikes)
    }
}
