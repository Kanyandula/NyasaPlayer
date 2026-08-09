# AAOS Slice A4 — Favourites, Artist Liked Songs & Empty State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the driver a per-row unlike that does not move the row underneath them, and rebuild
the three Favourites screens around it.

**Architecture:** The list freezes at the first unlike, not on tab entry — before an unlike there
is nothing to hold back, and freezing on entry would snapshot an empty list whenever Favourites is
opened before Firestore's first emission. The freeze lives in `AutomotiveContentViewModel` so it
survives Activity recreation. The unlike affordance goes inside the shared `CarTrackRow` behind an
optional callback, so its 76dp touch target is enforced once.

**Spec:** `docs/superpowers/specs/2026-08-09-aaos-favourites-design.md`
(decisions D19–D25 in §7 are settled — do not re-litigate them)

**Tech stack:** Kotlin, Jetpack Compose, Hilt, JUnit 4, kotlinx-coroutines-test, Gradle Kotlin DSL,
Detekt, Lint.

## Global constraints

- **Max line length 120.** Trailing commas required on call and declaration sites. No wildcard
  imports. Import at the top of the file — no fully-qualified paths inline.
- **Detekt `maxIssues: 0`.** Its `source` set covers `src/main/java` only — **test sources are not
  scanned**.
- **Top-level constants are PascalCase** (`HeartSize`, not `HEART_SIZE`).
- **`MatchingDeclarationName` counts only class-like declarations**, not functions. A file with one
  enum plus functions **will** trip it; a file with only functions will not. (A3 lost time here.)
- **Composables emitting UI take `modifier: Modifier = Modifier`** as the first optional parameter.
- **Android Lint `ContentDescription` is error severity.** An icon with no adjacent text naming it
  requires a description.
- **Automotive touch targets are ≥76dp.** Use `Modifier.carTouchTarget()`, not an exact `size()` —
  see that modifier's own KDoc.
- **Flavored Gradle tasks:** `:automotive:assembleOemDebug`, `:automotive:testOemDebugUnitTest`,
  `:automotive:lintOemDebug`. The plain `Debug` variants do not exist.
- **Verify builds from Gradle's own `BUILD SUCCESSFUL` line.** `./gradlew … | tail -n` reports
  `tail`'s exit status, not Gradle's.
- **Commits:** subject ≤72 chars, no AI attribution, no `Co-Authored-By`, no "Generated with"
  footer. Hard project rule.
- **The eight existing screens must keep compiling throughout.**

## Sequencing

Task 1 is an isolated shared-component change whose existing callers are untouched by their
defaults. Task 2 is the risky logic and lands with its tests before any screen consumes it —
`openFavourites`/`closeFavourites`/`toggleFavourite` have no production caller until Task 6, which
is deliberate and mirrors A3. Tasks 3–5 are screens, Task 6 wires them, Task 7 is documentation.

## File structure

**Created**

| File | Responsibility |
|---|---|
| `automotive/.../ui/screens/CarEmptyFavouritesScreen.kt` | Screen 17 — empty heart state and Browse CTA |
| `automotive/src/test/.../viewmodel/FavouritesSnapshotTest.kt` | The eight cases in spec §6.1 |

**Modified**

| File | Change |
|---|---|
| `automotive/.../ui/components/CarTrackRow.kt` | Optional `onLikeToggle` + `isLiked`, heart, semantics |
| `automotive/.../viewmodel/AutomotiveContentViewModel.kt` | `favourites`, `pendingUnlikes`, three new functions, file suppress |
| `automotive/.../viewmodel/AutomotivePlayerViewModel.kt` | `reportUnlikeFailed()` |
| `automotive/.../ui/screens/CarFavouriteMusicScreen.kt` | Rebuilt — hero, Play all, Shuffle, hearts |
| `automotive/.../ui/screens/CarArtistLikedSongsScreen.kt` | Artist hero, Play all, hearts; keeps Shuffle |
| `automotive/.../ui/AutomotiveApp.kt` | Tab effect, unlike callbacks, error routing |
| `automotive/src/test/.../fake/InertRepositoryFakes.kt` | `FakeUserRepository` becomes behavioural |
| `automotive/.../ui/preview/CarScreenPreviews.kt` | Updated for changed screen signatures |
| `docs/aaos-DESIGN.md` | Record D19–D25 |

**`CarScreenPreviews.kt` previews `CarArtistLikedSongsScreen` (line 192) but *not*
`CarFavouriteMusicScreen`** — verified, not assumed. So only **Task 5** needs to touch it. It has
broken on every screen signature change in A3, so if a build fails on it in another task, fix it
minimally and say so.

---

### Task 1: `CarTrackRow` gains an optional like affordance

Isolated and additive. Both new parameters default so that `CarHomeScreen` and `CarDetailScreen`
compile and render exactly as now, with no call-site changes.

The heart goes *inside* the shared row rather than into a new row variant or each call site, so
its 76dp touch target is enforced once, in the component (spec D22).

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarTrackRow.kt`

**Interfaces:**
- Produces: `CarTrackRow(title, artist, duration, isPlaying, onClick, modifier, coverUrl,
  onLikeToggle: (() -> Unit)? = null, isLiked: Boolean = true)`. Tasks 4 and 5 pass the last two.

- [ ] **Step 1: Add the constants**

Beside the existing private vals at the top of the file:

```kotlin
private val HeartGlyphSize = 32.dp
```

- [ ] **Step 2: Extend the signature**

Add two parameters after `coverUrl`, keeping `modifier` first among the optionals:

```kotlin
@Composable
fun CarTrackRow(
    title: String,
    artist: String,
    duration: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverUrl: String = "",
    onLikeToggle: (() -> Unit)? = null,
    isLiked: Boolean = true,
) {
```

- [ ] **Step 3: Give the row multi-action semantics**

The row body plays and the heart unlikes — two actions in one row. A rotary or screen-reader user
must be able to reach the second without hunting for a nested target. Replace the row's `Modifier`
chain:

```kotlin
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CarListRowHeight)
            .clickable(onClick = onClick)
            .then(
                if (onLikeToggle == null) {
                    Modifier
                } else {
                    Modifier.semantics {
                        stateDescription = if (isLiked) "Liked" else "Not liked"
                        customActions = listOf(
                            CustomAccessibilityAction(
                                label = if (isLiked) "Unlike" else "Like",
                                action = { onLikeToggle(); true },
                            ),
                        )
                    }
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
```

- [ ] **Step 4: Render the heart after the duration**

The duration `Text` is currently the last child. Add after it, still inside the `Row`:

```kotlin
        if (onLikeToggle != null) {
            Box(
                modifier = Modifier
                    .carTouchTarget()
                    .clickable(onClick = onLikeToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) NyasaGold else CarTextSecondary,
                    modifier = Modifier.size(HeartGlyphSize),
                )
            }
        }
```

`carTouchTarget()` supplies the 76dp floor around a 32dp glyph. `CarListRowHeight` is 80dp, so it
fits the existing row height. The `contentDescription` is required — Lint treats it as an error and
this icon has no adjacent text naming it. This matches `CarMiniPlayer`'s existing like control
(`CarMiniPlayer.kt:177-179`), which uses the same Material icons and tints.

- [ ] **Step 5: Add the imports**

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.nyasaplayer.auto.ui.components.carTouchTarget
```

`carTouchTarget()` is declared in `CarPrimitives.kt` in this same package — if the import is
flagged as redundant, drop it. `Box`, `Alignment` and `size` are already imported.

- [ ] **Step 6: Build**

Run: `./gradlew :automotive:assembleOemDebug :automotive:lintOemDebug detekt`
Expected: `BUILD SUCCESSFUL`. `CarHomeScreen` and `CarDetailScreen` must compile untouched — if
either needs a change, stop: the defaults are wrong.

- [ ] **Step 7: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarTrackRow.kt
git commit -m "feat: add an optional like affordance to the car track row"
```

---

### Task 2: The freeze, and the tests that pin it

The slice's real logic. TDD: the tests go in and fail before the implementation exists.

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotiveContentViewModel.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotivePlayerViewModel.kt`
- Modify: `automotive/src/test/java/com/example/nyasaplayer/auto/fake/InertRepositoryFakes.kt`
- Create: `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/FavouritesSnapshotTest.kt`

**Interfaces:**
- Consumes: `MainDispatcherRule` and the six fakes from A3's `automotive/src/test`.
- Produces: `AutomotiveContentState.favourites: List<Song>?`, `.pendingUnlikes: Set<String>`;
  `openFavourites()`, `closeFavourites()`, `suspend toggleFavourite(mediaId: String): Boolean`;
  `AutomotivePlayerViewModel.reportUnlikeFailed()`. Tasks 4–6 depend on all of these.

- [ ] **Step 1: Make `FakeUserRepository` behavioural**

In `InertRepositoryFakes.kt`, replace `FakeUserRepository`'s liked-songs and like/unlike members.
Leave the other members and the other two fakes alone.

```kotlin
class FakeUserRepository : UserRepository {
    val liked = MutableStateFlow<List<LikedSong>>(emptyList())

    /** Set to make the next likeSong/unlikeSong throw, so the revert path can be tested. */
    var failNextWrite: Boolean = false

    var likeCallCount = 0
    var unlikeCallCount = 0

    override fun getLikedSongs(userId: String): Flow<List<LikedSong>> = liked

    override suspend fun likeSong(userId: String, mediaId: String) {
        likeCallCount++
        if (failNextWrite) {
            failNextWrite = false
            error("write failed")
        }
    }

    override suspend fun unlikeSong(userId: String, mediaId: String) {
        unlikeCallCount++
        if (failNextWrite) {
            failNextWrite = false
            error("write failed")
        }
    }

    override fun getUserProfile(userId: String): Flow<UserProfile?> = flowOf(null)
    override suspend fun createOrUpdateProfile(profile: UserProfile) = Unit
    override fun isLiked(userId: String, mediaId: String): Flow<Boolean> = flowOf(false)
    override fun getRecentlyPlayed(userId: String, limit: Int): Flow<List<RecentlyPlayedEntry>> =
        flowOf(emptyList())
    override suspend fun logRecentlyPlayed(userId: String, mediaId: String) = Unit
    override suspend fun savePlaybackState(userId: String, state: PlaybackState) = Unit
    override suspend fun getPlaybackState(userId: String): PlaybackState? = null
}
```

Add `import kotlinx.coroutines.flow.MutableStateFlow`. `LikedSong`'s constructor is in
`core/common/.../models/UserData.kt` — read it before writing the test helpers in Step 2 and use
its real parameter names.

- [ ] **Step 2: Write the failing tests**

Create `FavouritesSnapshotTest.kt`. It follows `DetailLoadingTest`'s shape — read that file first
for the `viewModel()` helper and rule wiring, and match it.

```kotlin
package com.example.nyasaplayer.auto.viewmodel

import com.example.nyasaplayer.auto.MainDispatcherRule
import com.example.nyasaplayer.auto.fake.FakeAlbumRepository
import com.example.nyasaplayer.auto.fake.FakeAuthRepository
import com.example.nyasaplayer.auto.fake.FakeGenreRepository
import com.example.nyasaplayer.auto.fake.FakePlaylistRepository
import com.example.nyasaplayer.auto.fake.FakeSongRepository
import com.example.nyasaplayer.auto.fake.FakeUserRepository
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
```

Add a `likedSong(id)` helper matching `LikedSong`'s real constructor, read in Step 1.

**Cases 2 and 7 pull in opposite directions** — 2 requires the list keep tracking, 7 requires it
stop. An implementation satisfying only one looks correct in normal use.

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :automotive:testOemDebugUnitTest --tests "*FavouritesSnapshotTest*"`
Expected: FAIL — `favourites`, `pendingUnlikes`, `openFavourites`, `closeFavourites` and
`toggleFavourite` are all unresolved.

- [ ] **Step 4: Add the state fields**

In `AutomotiveContentState`, after `playlists`:

```kotlin
    val favourites: List<Song>? = null,
    val pendingUnlikes: Set<String> = emptySet(),
```

Null means no freeze is in effect and the screen renders live `likedSongs`. See spec D19 for why
this is not frozen on tab entry.

- [ ] **Step 5: Add the file-level suppression**

The file is at 19 functions against detekt's `thresholdInFiles: 20`, and the class-level
`@Suppress("TooManyFunctions")` added in A3 does **not** cover the file threshold. This task adds
four. Put this as the very first line of the file, above the `package` declaration:

```kotlin
@file:Suppress(
    // 19 functions before A4, against detekt's thresholdInFiles of 20. The class-level suppression
    // does not cover the file threshold. This class now owns search, genres, albums, playlists,
    // recently-played, liked songs, popular, detail and favourites — the next slice to touch it
    // should split it rather than suppress again. See spec D23.
    "TooManyFunctions",
)
```

- [ ] **Step 6: Add the three functions plus the freeze helper**

After `closeDetail()`:

```kotlin
    /**
     * Marks a visit to the Favourites tab.
     *
     * Deliberately does not freeze (spec D19) and deliberately clears nothing (spec D20). The
     * effect that calls this re-runs on every Activity recreation — a night-mode flip mid-drive —
     * and anything cleared here would silently reconcile the driver's held-back rows.
     */
    fun openFavourites() = Unit

    /** Ends the visit. The next unlike starts a new freeze. */
    fun closeFavourites() {
        _contentState.update { it.copy(favourites = null, pendingUnlikes = emptySet()) }
    }

    /**
     * Toggles one song's liked state, optimistically.
     *
     * The first unlike of a visit freezes the list as it stands *before* the removal lands, so the
     * row cannot move under the driver (spec D19). Returns false when the write failed, having
     * already reverted the optimistic change; the caller surfaces the error.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun toggleFavourite(mediaId: String): Boolean {
        val userId = authRepository.currentUserId ?: return false
        val wasPending = mediaId in _contentState.value.pendingUnlikes
        _contentState.update { state ->
            state.copy(
                // Freeze on the first unlike only. Never re-freeze: a second call must not
                // recapture a list the live flow has already changed.
                favourites = state.favourites ?: state.likedSongs,
                pendingUnlikes = if (wasPending) {
                    state.pendingUnlikes - mediaId
                } else {
                    state.pendingUnlikes + mediaId
                },
            )
        }
        return try {
            if (wasPending) {
                userRepository.likeSong(userId, mediaId)
            } else {
                userRepository.unlikeSong(userId, mediaId)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling favourite $mediaId", e)
            _contentState.update { state ->
                state.copy(
                    pendingUnlikes = if (wasPending) {
                        state.pendingUnlikes + mediaId
                    } else {
                        state.pendingUnlikes - mediaId
                    },
                )
            }
            false
        }
    }
```

`openFavourites()` is deliberately a no-op today. It exists so the shell has a symmetric
open/close pair and so a later slice can hang per-visit setup off it without changing call sites.
If detekt objects to an empty function body, give it the same explanatory comment rather than
deleting it.

- [ ] **Step 7: Clear the freeze on user switch**

In `reloadUserContent()`, add `favourites = null` and `pendingUnlikes = emptySet()` to the existing
`_contentState.update { it.copy(...) }` block that already clears `recentlyPlayed`, `likedSongs`,
`favoriteArtists` and `playlists`. A previous account's freeze must not survive a sign-out.

- [ ] **Step 8: Add `reportUnlikeFailed()` to the player ViewModel**

In `AutomotivePlayerViewModel`, beside `reportEmptyGenrePlayback()`:

```kotlin
    /**
     * Surfaces a failed like/unlike write. Non-retryable: Retry would act on the transport, not on
     * the write that failed — see the PlayerError.isRetryable KDoc.
     */
    fun reportUnlikeFailed() {
        _uiState.update {
            it.copy(
                error = PlayerError(
                    title = "Couldn't Save",
                    message = "Your change to this song wasn't saved. Check your connection.",
                    isPlaybackError = false,
                ),
            )
        }
    }
```

`isRetryable` defaults to false, so the overlay shows Dismiss alone. Do not pass it explicitly.

- [ ] **Step 9: Run the tests**

Run: `./gradlew :automotive:testOemDebugUnitTest --tests "*FavouritesSnapshotTest*"`
Expected: PASS, all eight.

- [ ] **Step 10: Verify each test actually bites**

For each of the eight, remove the behaviour it targets, confirm it goes red, and restore it.
Report what you saw for at least cases 2 and 7 — those are the ones that pull in opposite
directions. A3 shipped a guard whose tests passed with the guard deleted; do not repeat it.

- [ ] **Step 11: Build and commit**

```bash
./gradlew :automotive:assembleOemDebug :automotive:lintOemDebug detekt
git add automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/ automotive/src/test/
git commit -m "feat: freeze the favourites list at the first unlike"
```

---

### Task 3: `CarEmptyFavouritesScreen` (screen 17)

Contract: *empty heart state, Browse Music CTA. Allowed; CTA routes to Browse root.* Not a
navigation destination — it renders in place at the Favourites tab root, so it stays at drill
depth 0 and can never be refused while driving (spec D21).

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarEmptyFavouritesScreen.kt`

**Interfaces:**
- Produces: `CarEmptyFavouritesScreen(onBrowseClick: () -> Unit, modifier: Modifier = Modifier)`.
  Task 4 calls it.

- [ ] **Step 1: Write the screen**

```kotlin
package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.components.CarPillButton
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary

private val GlyphSize = 96.dp
private val Spacing = 12.dp
private val TitleSize = 40.sp
private val BodySize = 22.sp

/**
 * Screen 17 — Favourites with nothing liked.
 *
 * Rendered in place by [CarFavouriteMusicScreen] rather than being a navigation destination.
 * Favourites is a tab root at drill depth 0; a destination would sit at depth 1, where
 * `maxContentDepth` can refuse it — blocking a driver from a screen whose only content is
 * "you have nothing yet". See spec D21.
 */
@Composable
fun CarEmptyFavouritesScreen(
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Filled.FavoriteBorder,
            // Decorative: the title below carries the same meaning.
            contentDescription = null,
            tint = CarTextSecondary,
            modifier = Modifier.size(GlyphSize),
        )
        Text(
            text = "No favourites yet",
            color = Color.White,
            fontSize = TitleSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Songs you like will appear here.",
            color = CarTextSecondary,
            fontSize = BodySize,
            textAlign = TextAlign.Center,
        )
        CarPillButton(
            label = "Browse Music",
            onClick = onBrowseClick,
            modifier = Modifier.padding(top = Spacing),
        )
    }
}
```

The file has no class-like declaration, so `MatchingDeclarationName` does not apply and no file
suppression is needed.

- [ ] **Step 2: Build**

Run: `./gradlew :automotive:assembleOemDebug :automotive:lintOemDebug detekt`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarEmptyFavouritesScreen.kt
git commit -m "feat: add the empty favourites screen"
```

---

### Task 4: Rebuild `CarFavouriteMusicScreen` (screen 8)

Contract: *hero liked songs, Play all, Shuffle, track rows, unlike.* Today it is a title and a
list — A2's deliberate placeholder.

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarFavouriteMusicScreen.kt`

**Interfaces:**
- Consumes: `CarTrackRow`'s `onLikeToggle`/`isLiked` (Task 1); `CarEmptyFavouritesScreen` (Task 3).
- Produces: `CarFavouriteMusicScreen(songs, pendingUnlikes, onSongClick, onPlayAll, onShuffle,
  onLikeToggle, onBrowseClick, modifier, currentlyPlayingMediaId, isPlaying, isLoading,
  errorMessage, onRetry)`. Task 6 calls it.

- [ ] **Step 1: Rewrite the screen**

```kotlin
private val SectionSpacing = 16.dp
private val ListPadding = 24.dp
private val HeroSpacing = 24.dp
private val HeroArtSize = 200.dp
private val TitleSize = 34.sp
private val SubtitleSize = 20.sp

/**
 * Screen 8 — liked songs.
 *
 * Rows render from [songs], which the caller supplies as the frozen list when one is in effect and
 * the live list otherwise. A row whose id is in [pendingUnlikes] shows a hollow heart but keeps its
 * place: the contract defers row removal until refresh, because a list that reflows under the
 * driver's finger turns a mis-tap into a wrong action. See spec D19.
 */
@Suppress("LongParameterList")
@Composable
fun CarFavouriteMusicScreen(
    songs: List<Song>,
    pendingUnlikes: Set<String>,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onLikeToggle: (Song) -> Unit,
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    when {
        errorMessage != null && songs.isEmpty() -> CarEmptyState(
            title = "Something went wrong",
            body = errorMessage,
            modifier = modifier,
            actionLabel = "Try again",
            onAction = onRetry,
        )

        isLoading && songs.isEmpty() -> FavouritesSkeleton(modifier = modifier)

        songs.isEmpty() -> CarEmptyFavouritesScreen(
            onBrowseClick = onBrowseClick,
            modifier = modifier,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = ListPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            item {
                FavouritesHero(
                    songs = songs,
                    onPlayAll = onPlayAll,
                    onShuffle = onShuffle,
                )
            }
            items(songs, key = { it.mediaId }) { song ->
                CarTrackRow(
                    title = song.title,
                    artist = song.resolvedArtistName,
                    duration = formatDuration(song.durationMs),
                    isPlaying = isPlaying && song.mediaId == currentlyPlayingMediaId,
                    onClick = { onSongClick(song) },
                    coverUrl = song.resolvedCoverUrl,
                    onLikeToggle = { onLikeToggle(song) },
                    isLiked = song.mediaId !in pendingUnlikes,
                )
            }
        }
    }
}

@Composable
private fun FavouritesHero(
    songs: List<Song>,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HeroSpacing),
    ) {
        AsyncImage(
            model = songs.firstOrNull()?.resolvedCoverUrl.orEmpty(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(HeroArtSize)
                .clip(RoundedCornerShape(CarCardCornerRadius))
                .background(CarRaised),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HeroSpacing / 2),
        ) {
            Text(
                text = "Liked Songs",
                color = Color.White,
                fontSize = TitleSize,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${songs.size} songs",
                color = CarTextSecondary,
                fontSize = SubtitleSize,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(HeroSpacing / 2)) {
                CarPillButton(label = "Play all", onClick = onPlayAll)
                CarPillButton(label = "Shuffle", onClick = onShuffle, filled = false)
            }
        }
    }
}

/** Static placeholders, no shimmer — the ambient layer is the app's only decorative motion. */
@Composable
private fun FavouritesSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        repeat(SkeletonRowCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CarListRowHeight)
                    .clip(RoundedCornerShape(CarCardCornerRadius))
                    .background(CarRaised),
            )
        }
    }
}
```

Add `private const val SkeletonRowCount = 4` beside the other constants, and the imports the new
code needs (`AsyncImage`, `ContentScale`, `clip`, `RoundedCornerShape`, `background`, `Box`,
`Column`, `Row`, `height`, `size`, `padding`, `weight` via `Modifier`, `CarPillButton`,
`CarCardCornerRadius`, `CarRaised`, `CarListRowHeight`). Remove any import the deleted code was the
only user of — detekt's formatting ruleset fails the build on unused imports and the compiler will
not warn you.

The skeleton's rows are `CarListRowHeight` (80dp) tall, matching the real rows. Four rows plus
spacing fits the content slot; do not add more without redoing the arithmetic — A3 shipped two
skeletons that silently clipped.

- [ ] **Step 2: Build**

Run: `./gradlew :automotive:assembleOemDebug :automotive:lintOemDebug detekt`
Expected: `BUILD SUCCESSFUL`.

`CarScreenPreviews.kt` does **not** preview this screen (verified), so no preview change is
expected here. If the build says otherwise, fix it minimally and report it.

- [ ] **Step 3: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarFavouriteMusicScreen.kt
git commit -m "feat: rebuild favourites with hero, play all and unlike"
```

---

### Task 5: Rebuild `CarArtistLikedSongsScreen` (screen 9)

Contract: *artist hero, Play all, track rows, unlike.* It keeps its existing Shuffle, which the
contract does not list — removing a working control would be a visible regression (spec D24).

**Its list stays a live filter over `likedSongs`, not a freeze.** That is deliberate: A3's D16
established that the artist screen must resolve nothing during the restore gap, and unlike here
therefore removes the row immediately. The inconsistency with screen 8 is recorded as D25.

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarArtistLikedSongsScreen.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/preview/CarScreenPreviews.kt`

**Interfaces:**
- Consumes: `CarTrackRow`'s `onLikeToggle`/`isLiked` (Task 1).
- Produces: `CarArtistLikedSongsScreen(artistName, likedSongs, onBackClick, onSongClick,
  onPlayAll, onShufflePlay, onLikeToggle, modifier, currentlyPlayingMediaId, isPlaying)`.
  Task 6 calls it.

- [ ] **Step 1: Extend the signature**

The current signature is:

```kotlin
@Suppress("LongParameterList")
@Composable
fun CarArtistLikedSongsScreen(
    artistName: String,
    likedSongs: List<Song>,
    onBackClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onShufflePlay: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
)
```

Add two required parameters before `modifier`, so `modifier` stays first among the optionals:

```kotlin
@Suppress("LongParameterList")
@Composable
fun CarArtistLikedSongsScreen(
    artistName: String,
    likedSongs: List<Song>,
    onBackClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onLikeToggle: (Song) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
)
```

`@Suppress("LongParameterList")` is already on this function — keep it.

- [ ] **Step 2: Add the hero, Play all and the hearts**

Keep the file's existing structure, back button and Shuffle control — this is an extension, not a
rewrite.

Replace the plain header with a hero row carrying the artist name, the liked count, and Play all
beside the existing Shuffle:

```kotlin
            Row(horizontalArrangement = Arrangement.spacedBy(HeroSpacing / 2)) {
                CarPillButton(label = "Play all", onClick = onPlayAll)
                CarPillButton(label = "Shuffle", onClick = onShufflePlay, filled = false)
            }
```

and pass the heart through to each row:

```kotlin
                    onLikeToggle = { onLikeToggle(song) },
                    isLiked = true,
```

`isLiked` is always true here: this list *is* the liked songs for that artist, and a row that is
unliked leaves the list immediately (D25), so there is no hollow-heart state to render.

- [ ] **Step 3: Update the preview**

`CarScreenPreviews.kt:192` calls this screen and will not compile against the new signature. Add
`onPlayAll = {}` and `onLikeToggle = {}` to that one call — nothing else.

- [ ] **Step 4: Build**

Run: `./gradlew :automotive:assembleOemDebug :automotive:lintOemDebug detekt`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarArtistLikedSongsScreen.kt \
        automotive/src/main/java/com/example/nyasaplayer/auto/ui/preview/CarScreenPreviews.kt
git commit -m "feat: add play all and unlike to the artist liked songs screen"
```

---

### Task 6: Wire the shell

Gives `openFavourites`/`closeFavourites`/`toggleFavourite` their first production callers.

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt`

**Interfaces:**
- Consumes: everything produced by Tasks 2, 4 and 5.

- [ ] **Step 1: Drive the visit from the active tab**

In `AuthenticatedApp`, beside the existing `LaunchedEffect(drillDown)`:

```kotlin
    // Symmetric with the detail effect. openFavourites() does not freeze — the freeze happens at
    // the first unlike (spec D19) — but closeFavourites() must run on leaving, or the next visit
    // inherits the previous visit's held-back rows.
    LaunchedEffect(currentScreen) {
        if (currentScreen == CarScreen.Favourites) {
            contentViewModel.openFavourites()
        } else {
            contentViewModel.closeFavourites()
        }
    }
```

- [ ] **Step 2: Add the unlike callback**

In `AuthenticatedApp`, alongside the other callbacks passed to `BrowseShell`:

```kotlin
                onLikeToggle = { song ->
                    scope.launch {
                        if (!contentViewModel.toggleFavourite(song.mediaId)) {
                            playerViewModel.reportUnlikeFailed()
                        }
                    }
                },
```

`toggleFavourite` reverts the optimistic change itself and returns false; the shell only surfaces
the error. Add `onLikeToggle: (Song) -> Unit` to `BrowseShell`'s parameter list.

- [ ] **Step 3: Rewire the Favourites branch**

Replace the `CarScreen.Favourites ->` branch:

```kotlin
                    CarScreen.Favourites -> CarFavouriteMusicScreen(
                        songs = (contentState.favourites ?: contentState.likedSongs).take(maxItems),
                        pendingUnlikes = contentState.pendingUnlikes,
                        onSongClick = onLikedSongClick,
                        onPlayAll = { onPlayTracks(contentState.favourites ?: contentState.likedSongs) },
                        onShuffle = { onShuffleTracks(contentState.favourites ?: contentState.likedSongs) },
                        onLikeToggle = onLikeToggle,
                        onBrowseClick = { onSelectTab(CarScreen.Browse) },
                        currentlyPlayingMediaId = currentlyPlayingMediaId,
                        isPlaying = isPlaying,
                        isLoading = contentState.isLoading,
                        errorMessage = contentState.errorMessage,
                        onRetry = onRetry,
                    )
```

`favourites ?: likedSongs` is the freeze-or-live choice from spec §2.1, and `.take(maxItems)`
applies the driving cap **at render time**, not when the freeze is taken — truncation changes with
driving state, the freeze must not.

- [ ] **Step 4: Pass the heart to the artist screen**

In the `is CarDestination.Artist ->` branch, add to the `CarArtistLikedSongsScreen` call:

```kotlin
                                onPlayAll = { onPlayTracks(artistLikedSongs) },
                                onLikeToggle = onLikeToggle,
```

- [ ] **Step 5: Build and test**

Run: `./gradlew :automotive:assembleOemDebug :automotive:testOemDebugUnitTest :automotive:lintOemDebug detekt`
Expected: `BUILD SUCCESSFUL`; `FavouritesSnapshotTest` 8/8 and `DetailLoadingTest` 12/12 still pass.

- [ ] **Step 6: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt
git commit -m "feat: wire favourites unlike into the car shell"
```

---

### Task 7: Record D19–D25

**Files:**
- Modify: `docs/aaos-DESIGN.md`

- [ ] **Step 1: Add the entries**

Add D19 through D25 to the "Implementation deviations" section, after D18, matching the formatting
of the D11/D12/D14/D18 entries already there. Copy the decision text from spec §7 — do not reword
it. The three that matter most to a later reader:

- **D23** carries the note that the content ViewModel should be **split, not suppressed again**.
- **D24** records that screen 9 keeps a Shuffle the contract does not list.
- **D25** records that unlike removes the row on screen 9 but not on screen 8, and why preserving
  A3's D16 is worth that inconsistency.

- [ ] **Step 2: Commit**

```bash
git add docs/aaos-DESIGN.md
git commit -m "docs: record the A4 favourites decisions"
```

---

## Final verification

- [ ] `./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug` — both flavors.
- [ ] `./gradlew test :automotive:testOemDebugUnitTest` — `FavouritesSnapshotTest` 8/8,
      `DetailLoadingTest` 12/12, other suites green.
- [ ] `./gradlew :automotive:lintOemDebug detekt` — clean, `maxIssues: 0`.
- [ ] `CarHomeScreen` and `CarDetailScreen` render no heart — grep their `CarTrackRow` calls and
      confirm neither passes `onLikeToggle`.
- [ ] Touch targets ≥76dp, including the heart.
- [ ] Device pass per spec §6.2, **one emulator only** — see `docs/AAOS_A3_VERIFICATION.md` for why
      two starve the guest into swallowing taps. Record the outcome in `docs/` as A3 did; spec DoD
      item 12 requires it.
- [ ] The two device checks that cannot be seen any other way: **unlike then trigger a night-mode
      change without leaving the tab** — the row must still be there and still hollow (D20); and
      **open Favourites before liked songs have loaded** — they must appear when they arrive, not a
      permanent empty state (D19).
- [ ] Tap **every** CTA on both rebuilt screens. A3 shipped a silently dead card that nine reviews
      missed and one tap found.
