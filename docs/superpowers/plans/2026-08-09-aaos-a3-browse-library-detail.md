# AAOS Slice A3 — Browse, Library & Detail Screens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single-nullable drill-down with one `CarDestination` value, rebuild Browse
and Library against real data and the contract's category rows, and add album and playlist detail
screens fed by a cancellable, restore-safe detail loader.

**Architecture:** Navigation collapses to one `rememberSaveable` value holding a serializable
`CarDestination`. `AutomotiveContentViewModel` gains `PlaylistRepository` and a `detail` slice of
its state, loaded by a single `openDetail`/`closeDetail` pair driven from one `LaunchedEffect` in
the shell. Four private card composables collapse into one `CarContentCard`.

**Spec:** `docs/superpowers/specs/2026-08-09-aaos-browse-library-detail-design.md`
(decisions D8–D17 in §8 are settled — do not re-litigate them)

**Tech stack:** Kotlin, Jetpack Compose, Hilt, JUnit 4, kotlinx-coroutines-test, Gradle Kotlin DSL,
Detekt, Lint.

## Global constraints

- **Max line length 120.** Trailing commas required on call and declaration sites. No wildcard
  imports.
- **Detekt `maxIssues: 0`.** Run `./gradlew detekt` before every commit. Detekt's `source` set in
  the root `build.gradle.kts` covers `src/main/java` only — **test sources are not scanned**, so
  the fakes in Task 3 do not need to satisfy these rules. Production code does.
- **Top-level constants are PascalCase** in this repo (`DetailLoadError`, not
  `DETAIL_LOAD_ERROR`). `TopLevelPropertyNaming` uses `[A-Z][A-Za-z0-9]*`.
- **`MatchingDeclarationName` is on.** A file containing *exactly one* top-level class-like
  declaration must be named after it. A file with two or more is unconstrained — which is why
  `CarDetailState` can live beside `AutomotiveContentState` in the ViewModel file.
- **Composables emitting UI take `modifier: Modifier = Modifier`** as the first optional
  parameter.
- **Flavors exist.** Task names are `:automotive:assembleOemDebug`,
  `:automotive:testOemDebugUnitTest`, `:automotive:lintOemDebug` — not the plain `Debug` variants.
- **Verify on `AAOS_AOSP_33_userdebug`**, not the Play AVD. See
  `docs/AAOS_DRIVING_STATE_TESTING.md`.
- **Commits:** no AI attribution, no `Co-Authored-By` trailer. Subject ≤72 chars.
- **The eight existing screens must keep compiling and running throughout.**

## Sequencing

Task 1 is the navigation change and lands **alone**, before any screen work — spec §9 risk 1. It
touches the gate, eviction, tab switching and the artist screen's call site, and the artist
drill-down is the only one that already works, so it is the only one whose regression is
observable. Tasks 2–4 are the data layer and its tests, which the screens then consume. Tasks 5–8
are UI. Task 9 is documentation.

## File structure

**Created**

| File | Responsibility |
|---|---|
| `automotive/.../ui/navigation/CarDestination.kt` | The three drill-down destinations |
| `automotive/.../ui/components/CarContentCard.kt` | One card for album, playlist, genre, artist |
| `automotive/.../ui/screens/CarDetailScreen.kt` | Shared hero + track list body, plus the two thin screens over it |
| `automotive/src/test/.../MainDispatcherRule.kt` | `Dispatchers.setMain` for the module's first ViewModel test |
| `automotive/src/test/.../fake/FakeSongRepository.kt` | Order-preserving, unlike `:core:data`'s (spec §3.3) |
| `automotive/src/test/.../fake/FakeAlbumRepository.kt` | `getAlbumById` returns a configured album or null |
| `automotive/src/test/.../fake/FakePlaylistRepository.kt` | Emission-gated, so the restore case is testable |
| `automotive/src/test/.../fake/InertRepositoryFakes.kt` | Genre, User and Auth fakes — required to construct the ViewModel, no behaviour |
| `automotive/src/test/.../viewmodel/DetailLoadingTest.kt` | The nine cases in spec §7.1 |

**Modified**

| File | Change |
|---|---|
| `core/data/.../api/AuthRepository.kt` | Add `currentUserId: String?` |
| `core/data/.../FirebaseAuthRepository.kt` | Implement it |
| `core/data/src/test/.../fake/FakeAuthRepository.kt` | Implement it |
| `automotive/build.gradle.kts` | `testImplementation(libs.kotlinx.coroutines.test)` |
| `automotive/.../viewmodel/AutomotiveContentViewModel.kt` | `PlaylistRepository`, `observePlaylists()`, `CarDetailState`, `openDetail`/`closeDetail` |
| `automotive/.../ui/AutomotiveApp.kt` | `drillDown` replaces `selectedArtist`; detail routing; category and album callbacks rewired |
| `automotive/.../ui/screens/CarBrowseScreen.kt` | Rebuilt: genres in, search and hardcoded categories out |
| `automotive/.../ui/screens/CarLibraryScreen.kt` | Rebuilt: six contract rows |
| `docs/aaos-DESIGN.md` | Record D11, D12 and D14 |

**Deleted outright** — enumerated here because spec §9 risk 3 is losing behaviour that was never
written down. `CarBrowseScreen.kt`: `BrowseCategory`, `browseCategories`, `BrowseAllGrid`,
`CategoryCard`, `CarSearchBar`, `SearchResultItem`, `SearchEmptyState`, `FeaturedPlaylistsSection`,
`FeaturedPlaylistCard`. `CarLibraryScreen.kt`: `LikedSongsHeader`, `FavoriteArtistsSection`,
`ArtistAvatar`, `ArtistPlaceholder`, `AlbumListItem`.

**Kept, and named so they are not deleted by accident** — `CarBrowseScreen.kt`:
`computeScrollbarInfo`, `VerticalScrollbar`, `ScrollbarInfo` and their `Scrollbar*` constants.
`CarLibraryScreen.kt`: `LibraryHeader`, `SignOutConfirmationOverlay`, `SignOutModalCard`,
`SignOutActions` (D14), and **`LikedSongItem`** — it is `internal`, not private, because
`CarArtistLikedSongsScreen.kt:64` calls it. Deleting Library's liked-songs list does **not** make
it dead.

---

### Task 1: `CarDestination` and the single drill-down value

Lands alone. No screen gains a new destination in this task — `Album` and `Playlist` are declared
and unreachable until Task 8. What must still work at the end: tapping an artist in Library opens
the artist screen, back returns, switching tabs clears it, and driving evicts out of it.

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/CarDestination.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt`

**Interfaces:**
- Produces: `CarDestination` (sealed interface) with `Artist(artistId: String, artistName: String)`,
  `Album(albumId: String)`, `Playlist(playlistId: String)`. Tasks 4, 7 and 8 depend on these exact
  names.

- [ ] **Step 1: Create `CarDestination.kt`**

```kotlin
package com.example.nyasaplayer.auto.ui.navigation

/**
 * A destination reached by drilling in from a tab root. All three sit at drill depth 1 (D8).
 *
 * Identifiers and display strings only, never domain objects — this is stored in
 * `rememberSaveable`. [Artist] carries `artistName` because the artist screen needs it and
 * resolving it from state would drop the destination during the gap after process death, before
 * Firestore's first emission (D16).
 *
 * `java.io.Serializable` rather than `@Parcelize`: the kotlin-parcelize plugin is not applied to
 * `:automotive`, and both `CarScreen` and `FavoriteArtist` already take this route.
 */
sealed interface CarDestination : java.io.Serializable {
    data class Artist(val artistId: String, val artistName: String) : CarDestination
    data class Album(val albumId: String) : CarDestination
    data class Playlist(val playlistId: String) : CarDestination
}
```

Note the name collision: `CarDestination.Album` and `com.example.nyasaplayer.core.common.models.Album`
are both in scope in `AutomotiveApp.kt`. The nested form is always qualified, so no import alias is
needed — but never `import com.example.nyasaplayer.auto.ui.navigation.CarDestination.Album`.

- [ ] **Step 2: Swap the state value in `AuthenticatedApp`**

In `AutomotiveApp.kt`, replace line 106:

```kotlin
    var selectedArtist by rememberSaveable { mutableStateOf<FavoriteArtist?>(null) }
```

with:

```kotlin
    var drillDown by rememberSaveable { mutableStateOf<CarDestination?>(null) }
```

Add `import com.example.nyasaplayer.auto.ui.navigation.CarDestination`. Leave the
`FavoriteArtist` import — Library's artist row still hands one to the click callback.

- [ ] **Step 3: Rewrite `carUiLocation`**

Replace the function at `AutomotiveApp.kt:267-285`:

```kotlin
/**
 * Collapses the five scattered pieces of navigation state into the one value [gate] decides
 * on. Derived, not authoritative — the individual values stay where the screens read them.
 */
private fun carUiLocation(
    tab: CarScreen,
    showFullPlayer: Boolean,
    showQueue: Boolean,
    drillDown: CarDestination?,
    searchQuery: String,
): CarUiLocation = CarUiLocation(
    tab = tab,
    overlay = when {
        showFullPlayer -> CarOverlay.FullPlayer
        showQueue -> CarOverlay.Queue
        else -> null
    },
    // All three destinations are one step from a tab root (D8). There is no depth 2 in A3.
    drillDepth = if (drillDown != null) 1 else 0,
    // Settings and Profile are later slices, and Search is not yet a distinct sheet in this
    // app. The field exists so those slices have nothing to retrofit.
    sheet = null,
    textEntryActive = searchQuery.isNotEmpty(),
)
```

And its call site at `:110-116`: `selectedArtist = selectedArtist,` becomes `drillDown = drillDown,`.

- [ ] **Step 4: Clear one value in all three reset sites**

`AutomotiveApp.kt:127` (the gate's `Denied` branch) and `:167` (`onSelectTab`): change
`selectedArtist = null` to `drillDown = null`.

`:190-192`, the artist callbacks passed to `BrowseShell`:

```kotlin
                onArtistClick = { favoriteArtist ->
                    drillDown = CarDestination.Artist(
                        artistId = favoriteArtist.artistId,
                        artistName = favoriteArtist.artistName,
                    )
                },
                drillDown = drillDown,
                onBackFromDetail = { drillDown = null },
```

- [ ] **Step 5: Update `BrowseShell`'s parameters**

In the parameter list at `:305-307`, replace:

```kotlin
    selectedArtist: FavoriteArtist?,
    onBackFromArtist: () -> Unit,
```

with:

```kotlin
    drillDown: CarDestination?,
    onBackFromDetail: () -> Unit,
```

`onArtistClick: (FavoriteArtist) -> Unit` stays as-is — Library still emits a `FavoriteArtist` and
the shell maps it to a destination.

- [ ] **Step 6: Update the Library branch**

Replace `AutomotiveApp.kt:374-407` (`CarScreen.Library -> if (selectedArtist != null) { … }`):

```kotlin
                    CarScreen.Library -> {
                        val artist = drillDown as? CarDestination.Artist
                        if (artist != null) {
                            val artistLikedSongs = remember(
                                contentState.likedSongs,
                                artist.artistId,
                                maxItems,
                            ) {
                                contentState.likedSongs
                                    .filter { it.artistId == artist.artistId }
                                    .take(maxItems)
                            }
                            CarArtistLikedSongsScreen(
                                artistName = artist.artistName,
                                likedSongs = artistLikedSongs,
                                onBackClick = onBackFromDetail,
                                onSongClick = { song -> onArtistSongClick(artistLikedSongs, song) },
                                onShufflePlay = { onShuffleArtistSongs(artistLikedSongs) },
                                currentlyPlayingMediaId = currentlyPlayingMediaId,
                                isPlaying = isPlaying,
                            )
                        } else {
                            CarLibraryScreen(
                                favoriteArtists = contentState.favoriteArtists.take(maxItems),
                                albums = contentState.albums.take(maxItems),
                                onArtistClick = onArtistClick,
                                onAlbumClick = onAlbumClick,
                                likedSongs = contentState.likedSongs.take(maxItems),
                                currentlyPlayingMediaId = currentlyPlayingMediaId,
                                isPlaying = isPlaying,
                                onShuffleLikedSongs = onShuffleLikedSongs,
                                onLikedSongClick = onLikedSongClick,
                                onSignOut = onSignOut,
                                userDisplayName = userDisplayName,
                            )
                        }
                    }
```

`as? CarDestination.Artist` rather than an `is` check on `drillDown` directly: smart-casting a
`var` captured in a composable lambda does not compile. `Album` and `Playlist` fall to the `else`
branch and render Library, which is correct — nothing produces them yet.

- [ ] **Step 7: Build and run Detekt**

Run: `./gradlew :automotive:assembleOemDebug :automotive:testOemDebugUnitTest detekt`
Expected: BUILD SUCCESSFUL. `CarRestrictionGateTest` still passes — the gate is unmodified.

- [ ] **Step 8: Verify the drill-down on the emulator**

Boot `AAOS_AOSP_33_userdebug`, install, sign in. Then, per
`docs/AAOS_DRIVING_STATE_TESTING.md`:
1. Library → tap an artist → the artist screen opens on that artist.
2. Back → Library.
3. Library → tap an artist → tap the Browse rail item → returning to Library shows Library, not
   the artist.
4. Library → tap an artist → set driving state → evicted to Library with the restriction dialog.
5. Library → tap an artist → background the app → `adb shell am kill com.example.nyasaplayer.auto`
   → resume → the artist screen comes back on the same artist.

Check 5 is the one this task exists to protect. If it fails, `CarDestination` is not surviving the
`rememberSaveable` bundle — check that every nested class is a `data class` and the interface
extends `java.io.Serializable`.

- [ ] **Step 9: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/CarDestination.kt \
        automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt
git commit -m "refactor: hold one drill-down destination, not one nullable artist"
```

---

### Task 2: `currentUserId` and `PlaylistRepository` in the content ViewModel

Two changes with one purpose: make user-scoped playlist data reachable from the car, and reachable
from a unit test without a mocking framework.

`AuthRepository.currentUser` is typed `FirebaseUser?` — an abstract Firebase SDK class with no
public constructor. `:core:data`'s existing `FakeAuthRepository` holds a
`MutableStateFlow<FirebaseUser?>` that no test ever populates, and there is no mocking library in
`gradle/libs.versions.toml`. So **no test can currently produce a signed-in user id**, and Task 4
needs one. Adding `currentUserId: String?` to the interface is the fix: three lines of production
code, no new dependency, and it moves toward the `// TODO: Phase 3 — replace FirebaseUser` note
the interface already carries. `currentUser` stays; `:app`'s eleven call sites are not migrated —
that is not A3's work.

**Files:**
- Modify: `core/data/src/main/java/com/example/nyasaplayer/core/data/api/AuthRepository.kt`
- Modify: `core/data/src/main/java/com/example/nyasaplayer/core/data/FirebaseAuthRepository.kt`
- Modify: `core/data/src/test/java/com/example/nyasaplayer/core/data/fake/FakeAuthRepository.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotiveContentViewModel.kt`

**Interfaces:**
- Produces: `AuthRepository.currentUserId: String?`;
  `AutomotiveContentState.playlists: List<Playlist>`. Tasks 3, 4 and 7 depend on both.

- [ ] **Step 1: Add `currentUserId` to the interface**

In `AuthRepository.kt`, below `val currentUser: FirebaseUser?`:

```kotlin
    /**
     * The signed-in user's id, or null.
     *
     * Exists so callers that only need the id do not have to reach through [currentUser] and
     * therefore through `FirebaseUser`, which cannot be constructed in a unit test.
     */
    val currentUserId: String?
```

- [ ] **Step 2: Implement it**

In `FirebaseAuthRepository.kt`, below the `currentUser` override:

```kotlin
    override val currentUserId: String? get() = firebaseAuth.currentUser?.uid
```

In `core/data/src/test/.../fake/FakeAuthRepository.kt`, below the `currentUser` override:

```kotlin
    var userId: String? = null

    override val currentUserId: String? get() = userId ?: user.value?.uid
```

- [ ] **Step 3: Run the existing `:core:data` tests**

Run: `./gradlew :core:data:test`
Expected: PASS. Nothing reads `currentUserId` yet; this confirms the interface change compiles
against every existing implementation and fake.

- [ ] **Step 4: Inject `PlaylistRepository` and add the state field**

In `AutomotiveContentViewModel.kt`, add to the constructor after `albumRepository`:

```kotlin
    private val playlistRepository: PlaylistRepository,
```

with `import com.example.nyasaplayer.core.data.api.PlaylistRepository` and
`import com.example.nyasaplayer.core.common.models.Playlist`. No Hilt module change is needed —
`RepositoryModule` binds `PlaylistRepository` in `SingletonComponent`, which `:automotive` already
sees.

Add the job field beside `albumsJob`:

```kotlin
    private var playlistsJob: Job? = null
```

And in `AutomotiveContentState`, after `likedSongs`:

```kotlin
    val playlists: List<Playlist> = emptyList(),
```

- [ ] **Step 5: Add `observePlaylists()`**

Modelled on `observeLikedSongs()`. Place it directly after that function:

```kotlin
    private fun observePlaylists() {
        val userId = authRepository.currentUserId ?: return
        playlistsJob = playlistRepository.getPlaylists(userId).onEach { playlists ->
            _contentState.update { it.copy(playlists = playlists) }
        }.catch { e ->
            Log.e(TAG, "Error observing playlists", e)
        }.launchIn(viewModelScope)
    }
```

- [ ] **Step 6: Wire it into the three lifecycle points**

`cancelContentJobs()` gains `playlistsJob?.cancel()` after `likedSongsJob?.cancel()`.

`loadContent()` gains `observePlaylists()` after `observeLikedSongs()`.

`reloadUserContent()` — this is the user-switch leak guard (spec §9 risk 2). It becomes:

```kotlin
    fun reloadUserContent() {
        val newUserId = authRepository.currentUserId
        if (newUserId == currentUserId) return
        currentUserId = newUserId
        recentlyPlayedJob?.cancel()
        likedSongsJob?.cancel()
        playlistsJob?.cancel()
        _contentState.update {
            it.copy(
                recentlyPlayed = emptyList(),
                likedSongs = emptyList(),
                favoriteArtists = emptyList(),
                playlists = emptyList(),
            )
        }
        loadRecentlyPlayed()
        observeLikedSongs()
        observePlaylists()
    }
```

Clearing `playlists` in the same `update` as the other user-scoped lists is what stops the
previous account's playlists rendering for a frame after a switch.

- [ ] **Step 7: Migrate `:automotive`'s remaining `currentUser?.uid` reads**

`AutomotiveContentViewModel.kt:130` and `:150` — `loadRecentlyPlayed()` and `observeLikedSongs()`
— change `authRepository.currentUser?.uid` to `authRepository.currentUserId`. Same for
`AutomotivePlayerViewModel.kt:54`. Leave `AutomotiveAuthViewModel.kt:67` alone; it reads
`displayName`, not the id.

- [ ] **Step 8: Build**

Run: `./gradlew :automotive:assembleOemDebug :core:data:test detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add core/data/src/main/java/com/example/nyasaplayer/core/data/api/AuthRepository.kt \
        core/data/src/main/java/com/example/nyasaplayer/core/data/FirebaseAuthRepository.kt \
        core/data/src/test/java/com/example/nyasaplayer/core/data/fake/FakeAuthRepository.kt \
        automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/
git commit -m "feat: observe playlists in the car content view model"
```

---

### Task 3: Test harness for `:automotive`

This is the module's first ViewModel test. `UxFlagsTest`, `CarRestrictionGateTest` and
`DecorativeMotionTest` are pure-function tests, and the module declares
`testImplementation(libs.junit)` alone. `AutomotiveContentViewModel`'s `init { loadContent() }`
touches `viewModelScope`, so **constructing it at all** requires a main dispatcher.

Deliverable: a smoke test that constructs the ViewModel with six fakes and asserts its initial
state. If that passes, Task 4 is pure TDD.

**Files:**
- Modify: `automotive/build.gradle.kts`
- Create: `automotive/src/test/java/com/example/nyasaplayer/auto/MainDispatcherRule.kt`
- Create: `automotive/src/test/java/com/example/nyasaplayer/auto/fake/FakeSongRepository.kt`
- Create: `automotive/src/test/java/com/example/nyasaplayer/auto/fake/FakeAlbumRepository.kt`
- Create: `automotive/src/test/java/com/example/nyasaplayer/auto/fake/FakePlaylistRepository.kt`
- Create: `automotive/src/test/java/com/example/nyasaplayer/auto/fake/InertRepositoryFakes.kt`
- Create: `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/DetailLoadingTest.kt`

**Interfaces:**
- Consumes: `AuthRepository.currentUserId` and `AutomotiveContentState.playlists` from Task 2.
- Produces: `MainDispatcherRule`, the four fake files, and `DetailLoadingTest`'s `viewModel()`
  helper. Task 4 adds cases to the same test class.

- [ ] **Step 1: Add the test dependency**

In `automotive/build.gradle.kts`, replace `testImplementation(libs.junit)` with:

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
```

The alias already exists in `gradle/libs.versions.toml:55` and pins to `kotlinxCoroutinesCore`, so
there is no version to choose.

- [ ] **Step 2: Write `MainDispatcherRule`**

```kotlin
package com.example.nyasaplayer.auto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Installs a test dispatcher as `Dispatchers.Main` for the duration of a test.
 *
 * `AutomotiveContentViewModel`'s `init` block launches into `viewModelScope`, so the ViewModel
 * cannot even be constructed without this.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

- [ ] **Step 3: Write `FakeSongRepository`**

It **must** preserve request order. `:core:data`'s fake does not (it filters by a set), which is
why that one would never have caught an ordering regression — spec §3.3.

```kotlin
package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.api.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSongRepository : SongRepository {

    val songs = MutableStateFlow<List<Song>>(emptyList())

    /** Suspends every [getSongsByIds] call until released. Lets a test hold a load in flight. */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override fun getSongs(): Flow<List<Song>> = songs

    override suspend fun getSongsByIds(ids: List<String>): List<Song> {
        gate?.await()
        val byId = songs.value.associateBy { it.mediaId }
        // Request order, matching OfflineSongRepository's contract.
        return ids.mapNotNull { byId[it] }
    }

    override fun getSongsByArtist(artistId: String): Flow<List<Song>> = songs
    override fun getSongsByGenre(genreId: String): Flow<List<Song>> = songs
    override suspend fun getSongsByPopularity(limit: Int): List<Song> = songs.value.take(limit)
    override suspend fun searchSongs(query: String, limit: Int): List<Song> = emptyList()
}
```

Those six are `SongRepository`'s complete member list — no others need overriding.

- [ ] **Step 4: Write `FakeAlbumRepository`**

```kotlin
package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.data.api.AlbumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAlbumRepository : AlbumRepository {

    val albums = MutableStateFlow<List<Album>>(emptyList())

    override fun getAlbums(): Flow<List<Album>> = albums

    override suspend fun getAlbumById(albumId: String): Album? =
        albums.value.firstOrNull { it.id == albumId }

    override fun getAlbumsByArtist(artistId: String): Flow<List<Album>> = albums

    override suspend fun getAlbumsByPopularity(limit: Int): List<Album> = albums.value.take(limit)
}
```

- [ ] **Step 5: Write `FakePlaylistRepository`**

`replay = 1` is the point: `.first()` returns immediately if an emission has already happened, and
suspends if it has not. That is what makes spec §7.1 case 7 — the restore path — testable.

```kotlin
package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.data.api.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakePlaylistRepository : PlaylistRepository {

    private val emissions = MutableSharedFlow<List<Playlist>>(replay = 1)

    /** Nothing is emitted until a test calls this. Models Firestore's first-emission latency. */
    suspend fun emit(playlists: List<Playlist>) {
        emissions.emit(playlists)
    }

    override fun getPlaylists(userId: String): Flow<List<Playlist>> = emissions

    override suspend fun createPlaylist(userId: String, name: String): String =
        error("A3 wires read-only playlist access (D15)")

    override suspend fun addSongToPlaylist(userId: String, playlistId: String, mediaId: String) =
        error("A3 wires read-only playlist access (D15)")

    override suspend fun removeSongFromPlaylist(userId: String, playlistId: String, mediaId: String) =
        error("A3 wires read-only playlist access (D15)")

    override suspend fun deletePlaylist(userId: String, playlistId: String) =
        error("A3 wires read-only playlist access (D15)")
}
```

- [ ] **Step 6: Write `InertRepositoryFakes.kt`**

Three fakes with no behaviour. They exist because the ViewModel's constructor requires them —
without all six it cannot be built. One file with three top-level classes, which
`MatchingDeclarationName` permits (and test sources are not scanned anyway).

```kotlin
package com.example.nyasaplayer.auto.fake

import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.LikedSong
import com.example.nyasaplayer.core.common.models.PlaybackState
import com.example.nyasaplayer.core.common.models.RecentlyPlayedEntry
import com.example.nyasaplayer.core.common.models.UserProfile
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.AuthResult
import com.example.nyasaplayer.core.data.api.GenreRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeGenreRepository : GenreRepository {
    override fun getGenres(): Flow<List<Genre>> = flowOf(emptyList())
    override suspend fun getGenreById(genreId: String): Genre? = null
    override suspend fun getGenresByPopularity(limit: Int): List<Genre> = emptyList()
}

class FakeUserRepository : UserRepository {
    override fun getUserProfile(userId: String): Flow<UserProfile?> = flowOf(null)
    override suspend fun createOrUpdateProfile(profile: UserProfile) = Unit
    override fun getLikedSongs(userId: String): Flow<List<LikedSong>> = flowOf(emptyList())
    override suspend fun likeSong(userId: String, mediaId: String) = Unit
    override suspend fun unlikeSong(userId: String, mediaId: String) = Unit
    override fun isLiked(userId: String, mediaId: String): Flow<Boolean> = flowOf(false)
    override fun getRecentlyPlayed(userId: String, limit: Int): Flow<List<RecentlyPlayedEntry>> =
        flowOf(emptyList())
    override suspend fun logRecentlyPlayed(userId: String, mediaId: String) = Unit
    override suspend fun savePlaybackState(userId: String, state: PlaybackState) = Unit
    override suspend fun getPlaybackState(userId: String): PlaybackState? = null
}

/**
 * [currentUserId] is the only member any A3 test reads. [currentUser] stays null because
 * `FirebaseUser` is an abstract SDK class with no constructible form — the reason
 * `currentUserId` was added to the interface in Task 2.
 */
class FakeAuthRepository(override val currentUserId: String? = "test-user") : AuthRepository {
    override val currentUser: FirebaseUser? = null
    override val isAuthenticated: Boolean get() = currentUserId != null
    override suspend fun signInWithEmail(email: String, password: String): AuthResult =
        AuthResult.Error("unused")
    override suspend fun signUpWithEmail(email: String, password: String): AuthResult =
        AuthResult.Error("unused")
    override suspend fun signInWithCredential(credential: AuthCredential): AuthResult =
        AuthResult.Error("unused")
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = Result.success(Unit)
    override fun signOut() = Unit
}
```

`AuthResult` is a sealed class with `Success(user: FirebaseUser)` and `Error(message: String)`, so
`AuthResult.Error("unused")` compiles as written.

- [ ] **Step 7: Write the smoke test**

```kotlin
package com.example.nyasaplayer.auto.viewmodel

import com.example.nyasaplayer.auto.MainDispatcherRule
import com.example.nyasaplayer.auto.fake.FakeAlbumRepository
import com.example.nyasaplayer.auto.fake.FakeAuthRepository
import com.example.nyasaplayer.auto.fake.FakeGenreRepository
import com.example.nyasaplayer.auto.fake.FakePlaylistRepository
import com.example.nyasaplayer.auto.fake.FakeSongRepository
import com.example.nyasaplayer.auto.fake.FakeUserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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

    @Test
    fun newViewModel_hasNoDetail() = runTest {
        val vm = viewModel()
        assertNull(vm.contentState.value.detail)
        assertTrue(vm.contentState.value.playlists.isEmpty())
    }
}
```

This test references `contentState.value.detail`, which does not exist until Task 4. **Write the
`detail` field now** — add `val detail: CarDetailState? = null,` to `AutomotiveContentState` and
the `CarDetailState` data class beside it, exactly as Task 4 Step 3 specifies. The behaviour comes
in Task 4; the field is harness.

- [ ] **Step 8: Run it**

Run: `./gradlew :automotive:testOemDebugUnitTest --tests "*DetailLoadingTest*"`
Expected: PASS. If it fails with `Module with the Main dispatcher had failed to initialize`, the
`@get:Rule` annotation is wrong — it must be `@get:Rule`, not `@Rule`, on a `val`.

- [ ] **Step 9: Commit**

```bash
git add automotive/build.gradle.kts automotive/src/test/ \
        automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotiveContentViewModel.kt
git commit -m "test: add a view model test harness to the automotive module"
```

---

### Task 4: Detail loading

The only logic in A3 that can be silently wrong rather than visibly wrong. TDD throughout: the
nine cases come first, the loader second.

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotiveContentViewModel.kt`
- Modify: `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/DetailLoadingTest.kt`

**Interfaces:**
- Consumes: `CarDestination` (Task 1), the six fakes and `MainDispatcherRule` (Task 3).
- Produces: `AutomotiveContentViewModel.openDetail(destination: CarDestination)` and
  `closeDetail()`, both non-suspend, returning `Unit`. `CarDetailState` with fields `destination`,
  `title`, `subtitle`, `artworkUrl`, `tracks`, `isLoading`, `errorMessage`. Task 8 renders it.

- [ ] **Step 1: Write the failing tests**

Add to `DetailLoadingTest`, alongside the smoke test. Helper builders first:

```kotlin
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
```

Every `Song`, `Album` and `Playlist` parameter has a default, so these builders compile as written.
`Song.resolvedArtistName` falls back to `subtitle` when `artistName` is blank, which is why the
builder sets `artistName` explicitly.

```kotlin
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
        val gate = CompletableDeferred<Unit>()
        songs.gate = gate
        val vm = viewModel()

        vm.openDetail(CarDestination.Album("first"))
        vm.openDetail(CarDestination.Album("second"))
        songs.gate = null
        gate.complete(Unit)
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
```

Imports to add: `kotlinx.coroutines.CompletableDeferred`, `kotlinx.coroutines.test.advanceUntilIdle`,
`org.junit.Assert.assertEquals`, `assertFalse`, `assertNotNull`, the three model classes, and
`com.example.nyasaplayer.auto.ui.navigation.CarDestination`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :automotive:testOemDebugUnitTest --tests "*DetailLoadingTest*"`
Expected: FAIL — `openDetail`/`closeDetail` unresolved.

- [ ] **Step 3: Add `CarDetailState` and the state field**

At the bottom of `AutomotiveContentViewModel.kt`, beside `AutomotiveContentState` and
`FavoriteArtist`:

```kotlin
/**
 * One loaded detail screen — album or playlist.
 *
 * Artist detail is deliberately absent: its track list is a live filter over `likedSongs`, and
 * snapshotting it here would freeze the screen against unlikes performed on it (D16).
 */
data class CarDetailState(
    val destination: CarDestination,
    val title: String = "",
    val subtitle: String = "",
    val artworkUrl: String = "",
    val tracks: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
```

And in `AutomotiveContentState`, after `playlists`:

```kotlin
    val detail: CarDetailState? = null,
```

This imports `CarDestination` from `auto.ui.navigation` into `auto.viewmodel`. That is the price
of the spec's one-entry-point decision (§3.2) and is deliberate — the alternative is two public
methods that exist only to avoid naming a type.

- [ ] **Step 4: Add the error strings**

Beside the ViewModel's existing private constants (`TAG`, `PopularLimit`):

```kotlin
private const val DetailLoadError = "Could not load this. Check your connection and try again."
private const val AlbumMissingError = "This album is no longer available."
private const val PlaylistMissingError = "This playlist is no longer available."
```

- [ ] **Step 5: Write `openDetail` and `closeDetail`**

Add a `private var detailJob: Job? = null` beside `playlistsJob`, then, after `getSongsByGenre`:

```kotlin
    /**
     * Load the content behind [destination] into `contentState.detail`.
     *
     * Driven from one `LaunchedEffect(drillDown)` in the shell, which means this fires **once**
     * per destination and never re-runs when data arrives later. Everything it reads therefore
     * comes from a repository call, not from `_contentState` — including the call that follows
     * process death, when the observed flows have not emitted yet (D17).
     */
    fun openDetail(destination: CarDestination) {
        detailJob?.cancel()
        if (destination is CarDestination.Artist) {
            _contentState.update { it.copy(detail = null) }
            return
        }
        _contentState.update { it.copy(detail = CarDetailState(destination = destination)) }
        detailJob = viewModelScope.launch(exceptionHandler) {
            val loaded = loadDetail(destination)
            _contentState.update { state ->
                // The guard belongs inside the atomic update, not in a pre-read: a
                // check-then-write pair is a race in exactly the scenario it exists to prevent.
                // Cancellation alone is not enough — a coroutine suspended in a repository call
                // resumes and can reach here before the cancellation is observed.
                if (state.detail?.destination == destination) state.copy(detail = loaded) else state
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _contentState.update { it.copy(detail = null) }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadDetail(destination: CarDestination): CarDetailState = try {
        when (destination) {
            is CarDestination.Album -> loadAlbumDetail(destination)
            is CarDestination.Playlist -> loadPlaylistDetail(destination)
            // Filtered out by openDetail; a when over a sealed interface must be exhaustive.
            is CarDestination.Artist -> CarDetailState(destination, isLoading = false)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Error loading detail for $destination", e)
        CarDetailState(destination = destination, isLoading = false, errorMessage = DetailLoadError)
    }

    private suspend fun loadAlbumDetail(destination: CarDestination.Album): CarDetailState {
        val album = albumRepository.getAlbumById(destination.albumId)
            ?: return CarDetailState(destination, isLoading = false, errorMessage = AlbumMissingError)
        val tracks = songRepository.getSongsByIds(album.songIds)
        return CarDetailState(
            destination = destination,
            title = album.name,
            subtitle = album.artistName,
            artworkUrl = album.imageUrl,
            tracks = tracks,
            isLoading = false,
        )
    }

    private suspend fun loadPlaylistDetail(destination: CarDestination.Playlist): CarDetailState {
        val userId = authRepository.currentUserId
            ?: return CarDetailState(destination, isLoading = false, errorMessage = PlaylistMissingError)
        // first(), not contentState.playlists: this suspends until Firestore's first emission
        // rather than racing it. Playlist has no getPlaylistById to read one-shot (D17).
        val playlist = playlistRepository.getPlaylists(userId).first()
            .firstOrNull { it.id == destination.playlistId }
            ?: return CarDetailState(destination, isLoading = false, errorMessage = PlaylistMissingError)
        val tracks = songRepository.getSongsByIds(playlist.songIds)
        return CarDetailState(
            destination = destination,
            title = playlist.name,
            // Playlist has no cover field; artwork is the first resolved track's, same
            // derivation deriveFavoriteArtists() uses for artist avatars.
            artworkUrl = tracks.firstOrNull()?.resolvedCoverUrl.orEmpty(),
            tracks = tracks,
            isLoading = false,
        )
    }
```

Add `import kotlinx.coroutines.flow.first`.

- [ ] **Step 6: Delete `getSongsByAlbum`**

It fetches the `Album` and discards it, so it cannot serve the hero. Its only caller is
`AutomotiveApp.kt:183`, which Task 7 rewrites. Delete the function now and fix that call site to
open the destination instead:

```kotlin
                onAlbumClick = { album -> drillDown = CarDestination.Album(album.id) },
```

`scope`/`rememberCoroutineScope` stays — `onCategoryClick` still uses it until Task 6.

- [ ] **Step 7: Run the tests**

Run: `./gradlew :automotive:testOemDebugUnitTest --tests "*DetailLoadingTest*"`
Expected: PASS, all ten tests (nine cases plus the smoke test).

If `openDetail_playlistBeforeFirstEmission_resolvesWhenItArrives` hangs, `FakePlaylistRepository`
is missing `replay = 1`. If `openDetail_secondCallInFlight_firstResultIsDiscarded` fails with the
first album's tracks, the guard was written as a pre-read of `_contentState.value` instead of
inside the `update` block.

- [ ] **Step 8: Build and commit**

```bash
./gradlew :automotive:assembleOemDebug detekt
git add automotive/src/main/java/com/example/nyasaplayer/auto/ \
        automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/DetailLoadingTest.kt
git commit -m "feat: load album and playlist detail behind one cancellable job"
```

---

### Task 5: `CarContentCard`

`AAOS_SCREEN_CONTRACT.md` §Shared Component Inventory requires one card for "album, playlist, mix,
genre, recommendation" and prefers one shared component with variants over local copies. There are
four private copies today; Tasks 6 and 7 would otherwise add two more.

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarContentCard.kt`

**Interfaces:**
- Produces: `CarCardShape` enum (`Square`, `Circle`) and
  `CarContentCard(title, onClick, modifier, subtitle, artworkUrl, shape, isPlaying, enabled)`.
  Tasks 6, 7 and 8 all call it.

- [ ] **Step 1: Write the component**

```kotlin
package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarTextDisabled
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.core.common.ui.icons.MusicNoteIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold
import com.example.nyasaplayer.core.common.ui.theme.NyasaOnGold

/** Square for albums, playlists and genres; circle for artists. */
enum class CarCardShape { Square, Circle }

private val CardWidth = 180.dp
private val ArtSize = 180.dp
private val LabelSpacing = 10.dp
private val PlaceholderIconSize = 48.dp
private val TitleSize = 18.sp
private val SubtitleSize = 15.sp
private const val DisabledAlpha = 0.4f

/**
 * One card for album, playlist, genre and artist.
 *
 * Replaces `CategoryCard`, `FeaturedPlaylistCard`, `AlbumListItem` and `ArtistAvatar`. Its four
 * states are the inventory's required set: normal, focused (the system focus ring, not painted
 * here), playing ([isPlaying] golds the title) and unavailable ([enabled] = false).
 */
@Composable
fun CarContentCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    artworkUrl: String = "",
    shape: CarCardShape = CarCardShape.Square,
    isPlaying: Boolean = false,
    enabled: Boolean = true,
) {
    val cardShape = when (shape) {
        CarCardShape.Square -> RoundedCornerShape(CarCardCornerRadius)
        CarCardShape.Circle -> CircleShape
    }
    Column(
        modifier = modifier
            .width(CardWidth)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SubcomposeAsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(ArtSize)
                .clip(cardShape)
                .background(CarRaised),
            loading = { CardPlaceholder() },
            error = { CardPlaceholder() },
        )
        Text(
            text = title,
            color = when {
                !enabled -> CarTextDisabled
                isPlaying -> NyasaGold
                else -> Color.White
            },
            fontSize = TitleSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LabelSpacing),
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = CarTextSecondary,
                fontSize = SubtitleSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CardPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaGold),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MusicNoteIcon,
            contentDescription = null,
            tint = NyasaOnGold,
            modifier = Modifier.size(PlaceholderIconSize),
        )
    }
}
```

`contentDescription = null` on the artwork is correct and will not trip `app/lint.xml`'s
`ContentDescription` error: the title `Text` directly below it carries the same information, so
announcing the image separately would double-read every card.

Card width and art size are both 180dp, comfortably over the 76dp touch-target floor, so
`carTouchTarget()` is not needed here.

- [ ] **Step 2: Build**

Run: `./gradlew :automotive:assembleOemDebug detekt`
Expected: BUILD SUCCESSFUL. Detekt's compose rules will complain if `modifier` is not the first
optional parameter — it is.

- [ ] **Step 3: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarContentCard.kt
git commit -m "feat: add the shared car content card"
```

---

### Task 6: Rebuild `CarBrowseScreen`

Contract screen 4: *filter chips, genre/mood/category cards, Play/open category.* Ships as a grid
of genre cards from real Firestore data. No chips (D11 — `Genre` has no field backing "mood" or
"category"), no search (D10 — A6 owns it), no genre detail screen (D9 — tapping shuffle-plays).

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarBrowseScreen.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt`

**Interfaces:**
- Consumes: `CarContentCard`, `CarCardShape` (Task 5).
- Produces: `CarBrowseScreen(genres, onGenreClick, modifier, isLoading, errorMessage, onRetry)`.

- [ ] **Step 1: Rewrite the screen**

Replace everything in `CarBrowseScreen.kt` **except** `computeScrollbarInfo`, `VerticalScrollbar`,
`ScrollbarInfo` and the `Scrollbar*` private constants, which are kept verbatim:

```kotlin
private val GridSpacing = 24.dp
private val ListPadding = 24.dp
private const val BrowseGridColumns = 3

/**
 * Browse.
 *
 * A grid of real genres. Tapping one shuffle-plays it — there is no `CarGenreScreen` among the
 * twenty, and playing on tap is what "Play/open category" means in its absence (D9).
 *
 * No filter chips: `Genre` is id, name, color, imageUrl, popularity and songIds, and nothing
 * backs "mood" or "category". Any chip set would be invented taxonomy (D11). No search field:
 * screen 4 lists none, screens 5 and 6 own it, and A6 relocates it (D10).
 */
@Composable
fun CarBrowseScreen(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    when {
        errorMessage != null && genres.isEmpty() -> CarEmptyState(
            title = "Something went wrong",
            body = errorMessage,
            modifier = modifier,
            actionLabel = "Try again",
            onAction = onRetry,
        )

        isLoading && genres.isEmpty() -> BrowseSkeleton(modifier = modifier)

        genres.isEmpty() -> CarEmptyState(
            title = "Nothing to browse yet",
            body = "Genres will appear here once your library has synced.",
            modifier = modifier,
        )

        else -> BrowseGrid(genres = genres, onGenreClick = onGenreClick, modifier = modifier)
    }
}

@Composable
private fun BrowseGrid(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = ScrollbarWidth + ScrollbarGap),
            contentPadding = PaddingValues(vertical = ListPadding),
            verticalArrangement = Arrangement.spacedBy(GridSpacing),
        ) {
            item { CarSectionHeader(title = "Browse by genre") }
            // Chunked rows rather than LazyVerticalGrid: the rest of this module lays out in
            // LazyColumn, and the scrollbar below reads LazyListState.
            items(genres.chunked(BrowseGridColumns)) { rowGenres ->
                Row(horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
                    rowGenres.forEach { genre ->
                        CarContentCard(
                            title = genre.name,
                            onClick = { onGenreClick(genre) },
                            artworkUrl = genre.imageUrl,
                        )
                    }
                }
            }
        }

        VerticalScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 16.dp, horizontal = 8.dp),
        )
    }
}

/** Static placeholders, no shimmer — the ambient layer is the app's only decorative motion. */
@Composable
private fun BrowseSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(GridSpacing),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
                repeat(BrowseGridColumns) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(CarCardCornerRadius))
                            .background(CarRaised),
                    )
                }
            }
        }
    }
}
```

Then delete every now-unused import. The compiler will not catch unused imports but Detekt's
formatting ruleset will — run it before committing.

- [ ] **Step 2: Rewire the call site**

In `AutomotiveApp.kt`, replace the `CarScreen.Browse ->` branch (`:356-372`):

```kotlin
                    CarScreen.Browse -> CarBrowseScreen(
                        genres = contentState.genres.take(maxItems),
                        onGenreClick = onGenreClick,
                        isLoading = contentState.isLoading,
                        errorMessage = contentState.errorMessage,
                        onRetry = onRetry,
                    )
```

In `BrowseShell`'s parameter list, replace `onCategoryClick: (String) -> Unit` with
`onGenreClick: (Genre) -> Unit`, and delete `searchQuery`, `searchResults`, `onSearchQueryChange`,
`onClearSearch`, `onSearchResultClick`. Add `import com.example.nyasaplayer.core.common.models.Genre`.

In `AuthenticatedApp`, replace the `onCategoryClick` lambda (`:201-212`) with:

```kotlin
                onGenreClick = { genre ->
                    scope.launch {
                        val songs = contentViewModel.getSongsByGenre(genre.id)
                        if (songs.isNotEmpty()) {
                            playerViewModel.shufflePlay(songs)
                            showFullPlayer = true
                        }
                    }
                },
```

and delete the `onSearchQueryChange`, `onClearSearch` and `onSearchResultClick` arguments
(`:219-224`). This removes the name-matching lookup that made a category card a silent no-op when
its name was absent from Firestore — spec §6.

`AutomotiveContentViewModel.onSearchQueryChange`, `clearSearch`, and the `searchQuery`/
`searchResults` state fields are **not** deleted: `carUiLocation` still reads `searchQuery` for
`textEntryActive`, and A6 needs them. They simply have no UI until then.

- [ ] **Step 3: Build and lint**

Run: `./gradlew :automotive:assembleOemDebug :automotive:lintOemDebug detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify on the emulator**

Parked: Browse shows real genre names from Firestore, not "Trending Now"/"Podcasts". Every card
plays something when tapped. No search field. Screenshot it.

- [ ] **Step 5: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarBrowseScreen.kt \
        automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt
git commit -m "feat: rebuild browse around real genres"
```

---

### Task 7: Rebuild `CarLibraryScreen`

Contract screen 7: *category rows for Playlists, Albums, Artists, Favourites, Downloads; recently
played.* Six horizontal carousels (D8), not tiles that open list screens.

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarLibraryScreen.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt`

**Interfaces:**
- Consumes: `CarContentCard`, `CarCardShape` (Task 5); `AutomotiveContentState.playlists` (Task 2).
- Produces: `CarLibraryScreen(recentlyPlayed, playlists, albums, favoriteArtists, likedSongCount,
  onSongClick, onPlaylistClick, onAlbumClick, onArtistClick, onFavouritesClick, onSignOut,
  modifier, userDisplayName, currentlyPlayingMediaId, isPlaying, isLoading, errorMessage, onRetry)`.

- [ ] **Step 1: Rewrite the screen body**

Keep `LibraryHeader`, `SignOutConfirmationOverlay`, `SignOutModalCard`, `SignOutActions` and
`LikedSongItem` verbatim. Delete `LikedSongsHeader`, `FavoriteArtistsSection`, `ArtistAvatar`,
`ArtistPlaceholder` and `AlbumListItem`. Replace `CarLibraryScreen` itself:

```kotlin
private val RowSpacing = 32.dp
private val CardSpacing = 24.dp
private val ListPadding = 24.dp
private val SkeletonCardSize = 180.dp
private const val SkeletonRowCount = 3
private const val SkeletonCardCount = 4

/**
 * Library.
 *
 * Six category rows, Recently played first. Rows put content one tap from a tab root, which is
 * what a moving vehicle needs — and it keeps album, playlist and artist detail at drill depth 1
 * rather than 2, where any real head unit's `maxContentDepth` would refuse them (D8).
 *
 * Favourites is one card showing the liked count, not a list: it is a shortcut to a rail
 * destination that already renders that list, and two surfaces rendering identical content is a
 * visible bug (A2 D2). Downloads renders visibly disabled rather than hidden, so Library does not
 * change shape when A8 lands (D13). Sign-out stays here until A7 owns screen 14 (D14).
 */
@Suppress("LongParameterList")
@Composable
fun CarLibraryScreen(
    recentlyPlayed: List<Song>,
    playlists: List<Playlist>,
    albums: List<Album>,
    favoriteArtists: List<FavoriteArtist>,
    likedSongCount: Int,
    onSongClick: (List<Song>, Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (FavoriteArtist) -> Unit,
    onFavouritesClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    userDisplayName: String = "",
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    val hasContent = recentlyPlayed.isNotEmpty() || playlists.isNotEmpty() ||
        albums.isNotEmpty() || favoriteArtists.isNotEmpty() || likedSongCount > 0

    Box(modifier = modifier.fillMaxSize()) {
        when {
            errorMessage != null && !hasContent -> CarEmptyState(
                title = "Something went wrong",
                body = errorMessage,
                actionLabel = "Try again",
                onAction = onRetry,
            )

            isLoading && !hasContent -> LibrarySkeleton()

            else -> LibraryRows(
                recentlyPlayed = recentlyPlayed,
                playlists = playlists,
                albums = albums,
                favoriteArtists = favoriteArtists,
                likedSongCount = likedSongCount,
                hasContent = hasContent,
                onSongClick = onSongClick,
                onPlaylistClick = onPlaylistClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onFavouritesClick = onFavouritesClick,
                onBrowseClick = onBrowseClick,
                userDisplayName = userDisplayName,
                onSignOutClick = { showSignOutConfirmation = true },
                currentlyPlayingMediaId = currentlyPlayingMediaId,
                isPlaying = isPlaying,
            )
        }

        if (showSignOutConfirmation) {
            SignOutConfirmationOverlay(
                onConfirm = onSignOut,
                onDismiss = { showSignOutConfirmation = false },
            )
        }
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun LibraryRows(
    recentlyPlayed: List<Song>,
    playlists: List<Playlist>,
    albums: List<Album>,
    favoriteArtists: List<FavoriteArtist>,
    likedSongCount: Int,
    hasContent: Boolean,
    onSongClick: (List<Song>, Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (FavoriteArtist) -> Unit,
    onFavouritesClick: () -> Unit,
    onBrowseClick: () -> Unit,
    userDisplayName: String,
    onSignOutClick: () -> Unit,
    currentlyPlayingMediaId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        item {
            LibraryHeader(userDisplayName = userDisplayName, onSignOutClick = onSignOutClick)
        }

        if (!hasContent) {
            item {
                CarEmptyState(
                    title = "Your library is empty",
                    body = "Play something and it will show up here.",
                    actionLabel = "Browse Music",
                    onAction = onBrowseClick,
                )
            }
        }

        // Every data row with no content is omitted rather than rendering an empty carousel.
        if (recentlyPlayed.isNotEmpty()) {
            item {
                LibraryRow(title = "Recently played") {
                    items(recentlyPlayed, key = { "recent_${it.mediaId}" }) { song ->
                        CarContentCard(
                            title = song.title,
                            onClick = { onSongClick(recentlyPlayed, song) },
                            subtitle = song.resolvedArtistName,
                            artworkUrl = song.resolvedCoverUrl,
                            isPlaying = isPlaying && song.mediaId == currentlyPlayingMediaId,
                        )
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item {
                LibraryRow(title = "Playlists") {
                    items(playlists, key = { "playlist_${it.id}" }) { playlist ->
                        CarContentCard(
                            title = playlist.name,
                            onClick = { onPlaylistClick(playlist) },
                            subtitle = "${playlist.songIds.size} songs",
                        )
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item {
                LibraryRow(title = "Albums") {
                    items(albums, key = { "album_${it.id}" }) { album ->
                        CarContentCard(
                            title = album.name,
                            onClick = { onAlbumClick(album) },
                            subtitle = album.artistName,
                            artworkUrl = album.imageUrl,
                        )
                    }
                }
            }
        }

        if (favoriteArtists.isNotEmpty()) {
            item {
                LibraryRow(title = "Artists") {
                    items(favoriteArtists, key = { "artist_${it.artistId}" }) { artist ->
                        CarContentCard(
                            title = artist.artistName,
                            onClick = { onArtistClick(artist) },
                            subtitle = "${artist.likedCount} liked",
                            artworkUrl = artist.coverUrl,
                            shape = CarCardShape.Circle,
                        )
                    }
                }
            }
        }

        if (likedSongCount > 0) {
            item {
                LibraryRow(title = "Favourites") {
                    item {
                        CarContentCard(
                            title = "Liked songs",
                            onClick = onFavouritesClick,
                            subtitle = "$likedSongCount songs",
                        )
                    }
                }
            }
        }

        // Never omitted: it carries no data by definition, and hiding it would change
        // Library's shape when A8 lands (D13).
        item {
            LibraryRow(title = "Downloads") {
                item {
                    CarContentCard(
                        title = "Downloads",
                        onClick = {},
                        subtitle = "Coming soon",
                        enabled = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    title: String,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier) {
        CarSectionHeader(title = title, modifier = Modifier.padding(bottom = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
            content = content,
        )
    }
}

/** Row-shaped placeholders, so the headings stay put and the screen does not jump. */
@Composable
private fun LibrarySkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        repeat(SkeletonRowCount) {
            Row(horizontalArrangement = Arrangement.spacedBy(CardSpacing)) {
                repeat(SkeletonCardCount) {
                    Box(
                        modifier = Modifier
                            .size(SkeletonCardSize)
                            .clip(RoundedCornerShape(CarCardCornerRadius))
                            .background(CarRaised),
                    )
                }
            }
        }
    }
}
```

Add imports for `androidx.compose.foundation.lazy.LazyListScope`,
`androidx.compose.foundation.lazy.LazyRow`, `androidx.compose.foundation.lazy.items`,
`com.example.nyasaplayer.core.common.models.Playlist`, `CarContentCard`, `CarCardShape`,
`CarEmptyState`, `CarSectionHeader`. Remove `ShufflePlayButton`, `NowPlayingOverlay`,
`SubcomposeAsyncImage`, `CircleShape`, `MusicNoteIcon`, `CarListArtSize` and any other import the
deleted composables were the only user of — except those `LikedSongItem` still needs.

The Favourites and Downloads rows use `item { }` rather than `items(…)` because each is a single
card. `LazyListScope` exposes both.

- [ ] **Step 2: Rewire the call site**

In `AutomotiveApp.kt`, replace the `CarLibraryScreen(…)` call inside the `else` branch of the
Library case:

```kotlin
                            CarLibraryScreen(
                                recentlyPlayed = contentState.recentlyPlayed.take(maxItems),
                                playlists = contentState.playlists.take(maxItems),
                                albums = contentState.albums.take(maxItems),
                                favoriteArtists = contentState.favoriteArtists.take(maxItems),
                                likedSongCount = contentState.likedSongs.size,
                                onSongClick = onSongClick,
                                onPlaylistClick = onPlaylistClick,
                                onAlbumClick = onAlbumClick,
                                onArtistClick = onArtistClick,
                                onFavouritesClick = { onSelectTab(CarScreen.Favourites) },
                                onBrowseClick = { onSelectTab(CarScreen.Browse) },
                                onSignOut = onSignOut,
                                userDisplayName = userDisplayName,
                                currentlyPlayingMediaId = currentlyPlayingMediaId,
                                isPlaying = isPlaying,
                                isLoading = contentState.isLoading,
                                errorMessage = contentState.errorMessage,
                                onRetry = onRetry,
                            )
```

`likedSongCount` is `contentState.likedSongs.size`, **not** the `take(maxItems)` size — it is a
count on a shortcut card, not a rendered list, so the driving item cap does not apply to it.

In `BrowseShell`'s parameters: change `onAlbumClick: (Album) -> Unit` to stay as-is, add
`onPlaylistClick: (Playlist) -> Unit`, and delete `onShuffleLikedSongs` and `onLikedSongClick` if
nothing else uses them — `CarFavouriteMusicScreen` still uses `onLikedSongClick`, so keep that one
and delete only `onShuffleLikedSongs`.

In `AuthenticatedApp`, add beside the `onAlbumClick` lambda from Task 4 Step 6:

```kotlin
                onPlaylistClick = { playlist -> drillDown = CarDestination.Playlist(playlist.id) },
```

and delete the `onShuffleLikedSongs` argument.

- [ ] **Step 3: Build and lint**

Run: `./gradlew :automotive:assembleOemDebug :automotive:lintOemDebug detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify on the emulator**

Parked: Library renders its rows with Recently played first and Downloads last. Downloads is
visibly dimmed and does not respond to taps. The Favourites card shows the liked count and
switches to the Favourites tab, which renders the list. Tapping an artist still opens the artist
screen. Tapping a playlist or album currently renders Library — Task 8 gives them a screen.
Screenshot it.

- [ ] **Step 5: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarLibraryScreen.kt \
        automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt
git commit -m "feat: rebuild library as the contract's category rows"
```

---

### Task 8: Album and playlist detail

Contract screens 11 and 10. Both read the same `CarDetailState`, so they are two thin composables
over one shared body. No Download button and no download-progress state (D12 — `SongDownloadManager`
is `@Singleton` in `:app`, which `:automotive` cannot depend on). No save/offline control on
playlist (D15 — `PlaylistRepository` has no such concept).

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarDetailScreen.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt`

**Interfaces:**
- Consumes: `CarDetailState` (Task 4), `CarTrackRow`, `CarEmptyState`, `CarPillButton`.
- Produces: `CarAlbumScreen(detail, onBackClick, onPlay, onShuffle, onSongClick, modifier,
  currentlyPlayingMediaId, isPlaying, onRetry)` and `CarPlaylistScreen(…)` with the same shape.

- [ ] **Step 1: Write the shared body and the two screens**

```kotlin
package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.auto.ui.components.CarEmptyState
import com.example.nyasaplayer.auto.ui.components.CarPillButton
import com.example.nyasaplayer.auto.ui.components.CarTrackRow
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.auto.viewmodel.CarDetailState
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.formatDuration

private val HeroArtSize = 200.dp
private val HeroSpacing = 24.dp
private val ListPadding = 24.dp
private val SkeletonRowHeight = 80.dp
private val TitleSize = 34.sp
private val SubtitleSize = 20.sp
private const val SkeletonRowCount = 4

/**
 * Album detail — screen 11.
 *
 * No Download button: the code that performs a download is `SongDownloadManager`, `@Singleton`
 * in `:app`, which `:automotive` does not and should not depend on. `DownloadRepository` is
 * reachable but is Room bookkeeping only, so wiring it alone would ship a button that
 * permanently claims a download is in progress (D12).
 */
@Composable
fun CarAlbumScreen(
    detail: CarDetailState,
    onBackClick: () -> Unit,
    onPlay: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    onRetry: () -> Unit = {},
) {
    CarDetailBody(
        detail = detail,
        emptyBody = "This album has no playable tracks.",
        onBackClick = onBackClick,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onSongClick = onSongClick,
        modifier = modifier,
        currentlyPlayingMediaId = currentlyPlayingMediaId,
        isPlaying = isPlaying,
        onRetry = onRetry,
    )
}

/**
 * Playlist detail — screen 10.
 *
 * The contract's "save/offline if supported" resolves to *not supported*: `PlaylistRepository`
 * has no offline or save concept, so neither control ships. Artwork is the first resolved
 * track's cover, since `Playlist` has no cover field.
 */
@Composable
fun CarPlaylistScreen(
    detail: CarDetailState,
    onBackClick: () -> Unit,
    onPlay: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    onRetry: () -> Unit = {},
) {
    CarDetailBody(
        detail = detail,
        emptyBody = "This playlist is empty.",
        onBackClick = onBackClick,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onSongClick = onSongClick,
        modifier = modifier,
        currentlyPlayingMediaId = currentlyPlayingMediaId,
        isPlaying = isPlaying,
        onRetry = onRetry,
    )
}

@Suppress("LongParameterList")
@Composable
private fun CarDetailBody(
    detail: CarDetailState,
    emptyBody: String,
    onBackClick: () -> Unit,
    onPlay: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    onRetry: () -> Unit = {},
) {
    val error = detail.errorMessage
    when {
        error != null -> CarEmptyState(
            title = "Something went wrong",
            body = error,
            modifier = modifier,
            actionLabel = "Try again",
            onAction = onRetry,
        )

        detail.isLoading -> DetailSkeleton(modifier = modifier)

        detail.tracks.isEmpty() -> CarEmptyState(
            title = detail.title,
            body = emptyBody,
            modifier = modifier,
            actionLabel = "Back",
            onAction = onBackClick,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = ListPadding),
            verticalArrangement = Arrangement.spacedBy(HeroSpacing),
        ) {
            item {
                DetailHero(
                    detail = detail,
                    onPlay = { onPlay(detail.tracks) },
                    onShuffle = { onShuffle(detail.tracks) },
                    onBackClick = onBackClick,
                )
            }
            items(detail.tracks, key = { it.mediaId }) { song ->
                CarTrackRow(
                    title = song.title,
                    artist = song.resolvedArtistName,
                    duration = formatDuration(song.durationMs),
                    isPlaying = isPlaying && song.mediaId == currentlyPlayingMediaId,
                    onClick = { onSongClick(detail.tracks, song) },
                    coverUrl = song.resolvedCoverUrl,
                )
            }
        }
    }
}

@Composable
private fun DetailHero(
    detail: CarDetailState,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HeroSpacing),
    ) {
        AsyncImage(
            model = detail.artworkUrl,
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
                text = detail.title,
                color = Color.White,
                fontSize = TitleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detailSubtitle(detail),
                color = CarTextSecondary,
                fontSize = SubtitleSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(HeroSpacing / 2)) {
                CarPillButton(label = "Play", onClick = onPlay)
                CarPillButton(label = "Shuffle", onClick = onShuffle, filled = false)
                CarPillButton(label = "Back", onClick = onBackClick, filled = false)
            }
        }
    }
}

private fun detailSubtitle(detail: CarDetailState): String {
    val count = "${detail.tracks.size} tracks"
    return if (detail.subtitle.isBlank()) count else "${detail.subtitle} · $count"
}

@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(HeroSpacing),
    ) {
        Box(
            modifier = Modifier
                .size(HeroArtSize)
                .clip(RoundedCornerShape(CarCardCornerRadius))
                .background(CarRaised),
        )
        repeat(SkeletonRowCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SkeletonRowHeight)
                    .clip(RoundedCornerShape(CarCardCornerRadius))
                    .background(CarRaised),
            )
        }
    }
}
```

The three CTAs are `CarPillButton`, which already pins `CarPillButtonHeight` (76dp) and applies
`carTouchTarget()`, so the touch-target gate in §7.3 is satisfied without extra work.

- [ ] **Step 2: Drive the loader from the shell**

In `AuthenticatedApp`, after the restriction `LaunchedEffect` at `:121-132`:

```kotlin
    // One entry point. openDetail early-returns for Artist, whose tracks are a live filter over
    // likedSongs rather than a snapshot (D16).
    LaunchedEffect(drillDown) {
        val destination = drillDown
        if (destination != null) {
            contentViewModel.openDetail(destination)
        } else {
            contentViewModel.closeDetail()
        }
    }
```

Written as an `if`/`else` rather than `drillDown?.let(…) ?: closeDetail()` — the elvis form works
only because `openDetail` returns a non-null `Unit`, which is a footgun for whoever changes its
return type.

- [ ] **Step 3: Route the two screens**

In `BrowseShell`, extend the Library branch from Task 1 Step 6. Replace `val artist = drillDown as?
CarDestination.Artist` and its `if`/`else` with a `when`:

```kotlin
                    CarScreen.Library -> when (val destination = drillDown) {
                        is CarDestination.Artist -> {
                            val artistLikedSongs = remember(
                                contentState.likedSongs,
                                destination.artistId,
                                maxItems,
                            ) {
                                contentState.likedSongs
                                    .filter { it.artistId == destination.artistId }
                                    .take(maxItems)
                            }
                            CarArtistLikedSongsScreen(
                                artistName = destination.artistName,
                                likedSongs = artistLikedSongs,
                                onBackClick = onBackFromDetail,
                                onSongClick = { song -> onArtistSongClick(artistLikedSongs, song) },
                                onShufflePlay = { onShuffleArtistSongs(artistLikedSongs) },
                                currentlyPlayingMediaId = currentlyPlayingMediaId,
                                isPlaying = isPlaying,
                            )
                        }

                        is CarDestination.Album, is CarDestination.Playlist -> DetailRoute(
                            destination = destination,
                            detail = contentState.detail,
                            maxItems = maxItems,
                            onBackClick = onBackFromDetail,
                            onPlayTracks = onPlayTracks,
                            onShuffleTracks = onShuffleTracks,
                            onSongClick = onSongClick,
                            currentlyPlayingMediaId = currentlyPlayingMediaId,
                            isPlaying = isPlaying,
                            onRetry = onRetry,
                        )

                        null -> CarLibraryScreen(
                            recentlyPlayed = contentState.recentlyPlayed.take(maxItems),
                            playlists = contentState.playlists.take(maxItems),
                            albums = contentState.albums.take(maxItems),
                            favoriteArtists = contentState.favoriteArtists.take(maxItems),
                            likedSongCount = contentState.likedSongs.size,
                            onSongClick = onSongClick,
                            onPlaylistClick = onPlaylistClick,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            onFavouritesClick = { onSelectTab(CarScreen.Favourites) },
                            onBrowseClick = { onSelectTab(CarScreen.Browse) },
                            onSignOut = onSignOut,
                            userDisplayName = userDisplayName,
                            currentlyPlayingMediaId = currentlyPlayingMediaId,
                            isPlaying = isPlaying,
                            isLoading = contentState.isLoading,
                            errorMessage = contentState.errorMessage,
                            onRetry = onRetry,
                        )
                    }
```

Two things about this `when`. `when (val destination = drillDown)` binds the value once, which is
what makes the smart cast legal where a bare `is` check on the `var` would not be. And the combined
`is Album, is Playlist ->` branch types `destination` as `CarDestination`, not either subtype —
which is fine, because `DetailRoute` takes the supertype and does its own dispatch.

And add the route helper at the bottom of `AutomotiveApp.kt`:

```kotlin
/**
 * Applies the driving item cap to a loaded detail and picks the screen.
 *
 * `contentState.detail` is null for one frame after [drillDown] changes, before `openDetail`'s
 * first state write lands — and for one frame after *that* it can still hold the previous
 * destination. Both cases render the loading state rather than falling through, which is what
 * stops the wrong tracks appearing under the right title on entry.
 */
@Suppress("LongParameterList")
@Composable
private fun DetailRoute(
    destination: CarDestination,
    detail: CarDetailState?,
    maxItems: Int,
    onBackClick: () -> Unit,
    onPlayTracks: (List<Song>) -> Unit,
    onShuffleTracks: (List<Song>) -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    currentlyPlayingMediaId: String?,
    isPlaying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val capped = remember(detail, destination, maxItems) {
        detail?.takeIf { it.destination == destination }
            ?.let { loaded -> loaded.copy(tracks = loaded.tracks.take(maxItems)) }
            ?: CarDetailState(destination = destination, isLoading = true)
    }

    when (destination) {
        is CarDestination.Album -> CarAlbumScreen(
            detail = capped,
            onBackClick = onBackClick,
            onPlay = onPlayTracks,
            onShuffle = onShuffleTracks,
            onSongClick = onSongClick,
            modifier = modifier,
            currentlyPlayingMediaId = currentlyPlayingMediaId,
            isPlaying = isPlaying,
            onRetry = onRetry,
        )

        is CarDestination.Playlist -> CarPlaylistScreen(
            detail = capped,
            onBackClick = onBackClick,
            onPlay = onPlayTracks,
            onShuffle = onShuffleTracks,
            onSongClick = onSongClick,
            modifier = modifier,
            currentlyPlayingMediaId = currentlyPlayingMediaId,
            isPlaying = isPlaying,
            onRetry = onRetry,
        )

        // Routed by the caller's own branch; a when over a sealed interface must be exhaustive.
        is CarDestination.Artist -> Unit
    }
}
```

- [ ] **Step 4: Rename the shuffle callback and add its play counterpart**

`onShuffleArtistSongs: (List<Song>) -> Unit` already shuffle-plays any list, and both detail
screens now use it, so its name is wrong. Rename it to `onShuffleTracks` everywhere — the
`BrowseShell` parameter, the `AuthenticatedApp` argument, and the artist screen's
`onShufflePlay = { onShuffleTracks(artistLikedSongs) }` call. Behaviour is unchanged.

Add the play counterpart to `BrowseShell`'s parameters:

```kotlin
    onPlayTracks: (List<Song>) -> Unit,
```

and in `AuthenticatedApp`:

```kotlin
                onPlayTracks = { tracks ->
                    tracks.firstOrNull()?.let { first ->
                        playerViewModel.playSong(tracks, first)
                        showFullPlayer = true
                    }
                },
```

`playSong` resolves its start index with `indexOfFirst { … }.coerceAtLeast(0)`, so passing the
list and its own first element is what makes Play start at track 1 rather than wherever the queue
happened to be.

- [ ] **Step 5: Build and test**

Run: `./gradlew :automotive:assembleOemDebug :automotive:testOemDebugUnitTest :automotive:lintOemDebug detekt`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Verify on the emulator**

Parked:
1. Library → tap an album → the album screen opens with hero, Play, Shuffle, Back and its tracks
   in album order.
2. Play plays from track 1; Shuffle shuffles; a track row plays that track.
3. Back returns to Library.
4. Same three checks for a playlist.
5. Background the app inside playlist detail, `adb shell am kill com.example.nyasaplayer.auto`,
   resume. The playlist screen must come back **with its tracks**. This is the D17 check.
6. Repeat 5 inside album detail and inside artist detail.

Driving:
7. Set the driving state while inside album detail → evicted to Library with the dialog.
8. With `maxContentDepth` injected as 0, entering album or playlist detail is refused. Per spec
   §7.2 check 4 this cannot be observed by driving the emulator — the userdebug AVD reports a cap
   of 3, and the rule is `drillDepth > maxContentDepth`, so depth 1 is correctly *allowed* at any
   cap of 1 or more.
9. Track lists truncate at `maxCumulativeContentItems`.

- [ ] **Step 7: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarDetailScreen.kt \
        automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt
git commit -m "feat: add album and playlist detail screens"
```

---

### Task 9: Record the contract deviations

D11, D12 and D14 are knowing deviations from `AAOS_SCREEN_CONTRACT.md`. §Phase Acceptance
Additions requires missing data and blocked capability to be recorded rather than filled with
placeholders, and D12 carries a module blocker A8 needs.

**Files:**
- Modify: `docs/aaos-DESIGN.md`

- [ ] **Step 1: Add the three entries**

Beside A2's D6 and D7 in `docs/aaos-DESIGN.md`, matching the surrounding format:

```markdown
- **D11 — No Browse filter chips.** Screen 4 lists them. `Genre` is `id`, `name`, `color`,
  `imageUrl`, `popularity`, `songIds` — nothing backs "mood" or "category", so any chip set would
  be invented taxonomy. **Data blocker:** a genre taxonomy field in Firestore. The grid is
  unchanged by chips arriving later.
- **D12 — No Download button on `CarAlbumScreen`, and no download-progress state.** Screen 11
  lists both. **Module blocker: downloads are unreachable from `:automotive` until
  `SongDownloadManager` leaves `:app`.** `DownloadRepository` (`:core:data`) is reachable but
  records download state in Room only — `addDownload`, `updateProgress`, `markCompleted`. The code
  that fetches and writes the file is `SongDownloadManager`, `@Singleton` in `:app`, and
  `:automotive` does not and should not depend on `:app`. Wiring the repository alone ships a
  button that permanently claims a download is in progress. Extracting the manager into a shared
  module has six `:app` call sites and belongs to A8, which owns downloads and needs it anyway.
- **D14 — Sign-out stays on `CarLibraryScreen`** with its confirmation overlay, marked for
  deletion in A7. It belongs on screen 14, but removing it in A3 leaves no way to sign out of the
  vehicle at all, since the system bar's avatar is disabled until A7 (A2 D3).
```

- [ ] **Step 2: Commit**

```bash
git add docs/aaos-DESIGN.md
git commit -m "docs: record the A3 contract deviations and the download blocker"
```

---

## Final verification

Spec §10's definition of done, in order. Every item is checked against the built app, not against
the plan.

- [ ] `./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug` — both flavors
      build (NFR-7).
- [ ] `./gradlew test :automotive:testOemDebugUnitTest` — all unit tests pass, including the ten
      in `DetailLoadingTest`.
- [ ] `./gradlew :automotive:lintOemDebug :app:lintDebug :core:common:lintDebug :core:data:lintDebug`
      — clean.
- [ ] `./gradlew detekt` — zero issues.
- [ ] `AutomotiveApp` holds exactly one drill-down value. Grep for `selectedArtist` — no hits.
- [ ] `gate()` and `GateResult.kt` are unmodified. `git diff main -- automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/GateResult.kt`
      is empty.
- [ ] Browse renders real genres, has no search field and no hardcoded category list. Grep for
      `browseCategories` — no hits.
- [ ] Library renders its rows; Downloads is visibly disabled; Favourites is a shortcut card, not
      a second copy of the liked list.
- [ ] `CarAlbumScreen` and `CarPlaylistScreen` are reachable from Library and play and shuffle
      their tracks.
- [ ] `PlaylistRepository` is cancelled and restarted on user switch — sign out, sign in as a
      second account, confirm the first account's playlists are gone (spec §7.2 check 7).
- [ ] `CarContentCard` replaced all four private card composables. Grep for `CategoryCard`,
      `FeaturedPlaylistCard`, `AlbumListItem`, `ArtistAvatar` — no hits.
- [ ] All three destinations survive process death with their content (spec §7.2 check 8).
- [ ] Cards and CTAs are at least 76dp on the smallest side.
- [ ] No new colour pairs — every token used is already measured in `aaos-DESIGN.md` §Contrast.
- [ ] All four screens screenshotted parked and driving, and the §7.2 checklist outcome recorded.