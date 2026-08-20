# AAOS Slice A6 - Search & Search Results Implementation Plan

> **For agentic workers:** Implement task-by-task. Steps use checkbox (`- [ ]`) syntax for
> tracking. Use `compose-skill` for the Compose UI/navigation tasks, especially Tasks 2-4. Do not
> skip the verification gates at the end.

**Goal:** Enable system-bar search in the `oem` custom launcher, resolve Q2 with system IME vs
`NO_KEYBOARD` behaviour, and ship song-only submitted search results that remain compliant while
driving.

**Architecture:** Search moves out of `AutomotiveContentViewModel` into a focused
`AutomotiveSearchViewModel` backed only by `SongRepository.searchSongs`. `AutomotiveApp` owns one
`showSearch` sheet flag, derives `CarUiLocation.sheet = CarSheet.Search` from it, and reports
`textEntryActive` from the search field's editing state rather than from a non-empty query.

**Spec:** `docs/superpowers/specs/2026-08-20-aaos-search-design.md`
(decisions D31-D35 are settled - do not re-litigate them during implementation)

**Tech stack:** Kotlin, Jetpack Compose, Media3 search path unchanged, Hilt, JUnit 4,
kotlinx-coroutines-test, Gradle Kotlin DSL, Detekt, Lint.

## Current main baseline

This plan was first written against the A5 merge baseline (`9dab4f7`, PR #24). `main` now includes
PR #25 and PR #26, ending at `6637508`, which changed the Favourites ViewModel tests and fixtures.
A6 must be rebased or merged onto that baseline before any remaining work continues.

The current A6 branch is **not up to date** until that happens. A diff from current `main` to the
A6 branch must not:

- delete `FavouritesBoundaryTest.kt`
- delete `FavouritesJourneyTest.kt`
- delete `FavouritesTestCase.kt`
- inline the shared Favourites fixture back into `FavouritesSnapshotTest.kt`
- simplify `FakeGenreRepository` or `FakeUserRepository` in a way that removes PR #26's error,
  per-user, or cancellation hooks
- remove `FakeSongRepository.throwOnceOnGetSongsByIds`
- revert `AutomotiveContentViewModel`'s Favourites error/loading channel

When Task 1 removes search ownership from `AutomotiveContentViewModel`, start from current `main`
and delete only the search-owned pieces: `searchJob`, `SearchLimit`, `SearchDebounceMs`,
`searchQuery`, `searchResults`, `onSearchQueryChange()`, `clearSearch()`, and imports made unused
by that deletion. Preserve `FavouritesLoadError`, `cancelCatalogueJobs()`, `cancelUserJobs()`,
`reportLikedSongsFailure()`, `favouritesError`, `favouritesLoading`, the null-user-id guard in
`reloadUserContent()`, the guarded user-collector restart in `loadContent()`, and
`openFavourites()`'s current freeze behavior.

## Global constraints

- **Max line length 120.** Trailing commas required on call and declaration sites. No wildcard
  imports.
- **Detekt `maxIssues: 0`.** Its configured source set covers production Kotlin; do not rely on
  test-source linting for production quality.
- **Top-level constants are PascalCase.**
- **Composables emitting UI take `modifier: Modifier = Modifier` as the first optional
  parameter.**
- **Automotive touch targets are at least 76dp.** Use `carTouchTarget()` or existing car button
  primitives.
- **No custom keyboard and no in-app voice recorder.** Do not add `RECORD_AUDIO`.
- **Flavored Gradle tasks:** use `:automotive:assembleOemDebug`,
  `:automotive:testOemDebugUnitTest`, `:automotive:lintOemDebug`; plain `Debug` variants do not
  exist.
- **Verify builds from Gradle's own `BUILD SUCCESSFUL` line.**
- **Keep `playstore` custom activity boundary intact.** `PlaybackService` search remains the
  host-rendered path.

## Sequencing

Task 0 brings the branch up to current `main` and protects the PR #26 Favourites fixtures. Task 1
is the data/search-state split and lands with JVM tests before UI consumes it. Task 2 wires
navigation and gate semantics. Task 3 builds the screens. Task 4 connects playback, caps and
shortcuts. Task 5 records the contract changes. Task 6 runs the full gate and device checklist.

## File structure

**Created**

| File | Responsibility |
|---|---|
| `automotive/.../viewmodel/AutomotiveSearchViewModel.kt` | Search state, submit, recent queries, retry, editing flag |
| `automotive/src/test/.../viewmodel/AutomotiveSearchViewModelTest.kt` | Search state and stale-result tests |
| `automotive/.../ui/screens/CarSearchScreen.kt` | Screen 5 idle/search-entry sheet |
| `automotive/.../ui/screens/CarSearchResultsScreen.kt` | Screen 6 submitted results sheet |
| `docs/AAOS_A6_VERIFICATION.md` | Manual verification record after device checks |

**Modified**

| File | Change |
|---|---|
| `core/data/.../local/dao/SongDao.kt` | Include `album_name` in `search()` |
| `core/data/src/test/.../fake/FakeSongDao.kt` | Match DAO search semantics |
| `core/data/src/test/.../offline/OfflineSongRepositoryTest.kt` | Test title, artist and album-name search |
| `automotive/src/test/.../fake/FakeSongRepository.kt` | Return configurable search results and call counts |
| `automotive/src/test/.../fake/InertRepositoryFakes.kt` | Preserve current-main Favourites helpers if conflicts touch this file |
| `automotive/.../viewmodel/AutomotiveContentViewModel.kt` | Remove search state/methods/job from the content ViewModel |
| `automotive/.../ui/AutomotiveApp.kt` | Add search ViewModel, sheet state, gate integration and screen routing |
| `automotive/.../ui/navigation/CarUiLocation.kt` | Update comment: text entry is editing state, not query presence |
| `automotive/.../ui/navigation/GateResult.kt` | Update stale comments about Search being unreachable |
| `automotive/.../ui/components/CarSystemBar.kt` | Enable only search; settings/profile remain disabled |
| `automotive/.../ui/preview/CarScreenPreviews.kt` | Add/update search previews if the file already covers adjacent screens |
| `docs/aaos-DESIGN.md` | Keep D31-D35 accurate if implementation revises an edge case |
| `docs/AAOS_PRD.md` | Keep A6 status honest after implementation |
| `docs/AAOS_SCREEN_CONTRACT.md` | Keep screen 6 aligned to song-only A6 results |
| `docs/tickets/A6-search.md` | Move status from spec/plan ready to implemented/verified when done |
| `docs/tickets/T4-automotive-multi-entity-search.md` | Preserve deferred album/artist/playlist result-card follow-up |

---

## Task 0: Bring A6 onto current `main`

Required before continuing implementation. PR #26 changed tracked Favourites tests and their shared
fixtures; A6 must not carry the older pre-PR #26 shape forward.

**Files to preserve from current `main`:**
- `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/FavouritesBoundaryTest.kt`
- `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/FavouritesJourneyTest.kt`
- `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/FavouritesSnapshotTest.kt`
- `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/FavouritesTestCase.kt`
- `automotive/src/test/java/com/example/nyasaplayer/auto/fake/InertRepositoryFakes.kt`
- `automotive/src/test/java/com/example/nyasaplayer/auto/fake/FakeSongRepository.kt`
- `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotiveContentViewModel.kt`
- `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt`

- [ ] Rebase or merge current `main` into the A6 branch.
- [ ] Re-apply A6 Task 1 changes on top of current `main`, not by accepting the old A6 side for
      conflict hunks.
- [ ] Keep the three Favourites suites and `FavouritesTestCase`; do not delete or inline them.
- [ ] Keep `DefaultUserId`, `FakeGenreRepository.genres`, `genresError`,
      `FakeUserRepository.likedFor()`, `likedSongsFlowError`, `throwOnNextWrite`, and
      per-account liked-song flows.
- [ ] Extend `FakeSongRepository` for search tests without removing `gate` or
      `throwOnceOnGetSongsByIds`.
- [ ] In `AutomotiveContentViewModel`, remove only search-owned state/methods; preserve the
      current Favourites error/loading code named in the baseline section above.
- [ ] In `AutomotiveApp`, preserve the current Favourites binding to `contentState.favouritesLoading`
      and `contentState.favouritesError`.
- [ ] Confirm `git diff --name-status main..HEAD` shows no deletion of
      `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/Favourites*`.

**Verify:**

```bash
./gradlew :automotive:testOemDebugUnitTest
```

---

## Task 1: Search ViewModel and song-search parity

The UI should consume one focused search state owner, not the already overloaded content
ViewModel. Add the new ViewModel and tests first, after Task 0 has brought the branch onto
current `main`.

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotiveSearchViewModel.kt`
- Create: `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/AutomotiveSearchViewModelTest.kt`
- Modify: `automotive/src/test/java/com/example/nyasaplayer/auto/fake/FakeSongRepository.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotiveContentViewModel.kt`
- Modify: `core/data/src/main/java/com/example/nyasaplayer/core/data/local/dao/SongDao.kt`
- Modify: `core/data/src/test/java/com/example/nyasaplayer/core/data/fake/FakeSongDao.kt`
- Modify: `core/data/src/test/java/com/example/nyasaplayer/core/data/offline/OfflineSongRepositoryTest.kt`

**Interfaces:**

```kotlin
data class AutomotiveSearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val results: List<Song> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isEditing: Boolean = false,
)
```

```kotlin
@HiltViewModel
class AutomotiveSearchViewModel @Inject constructor(
    private val songRepository: SongRepository,
) : ViewModel() {
    val uiState: StateFlow<AutomotiveSearchUiState>

    fun onQueryChange(query: String)
    fun submitSearch()
    fun selectRecentQuery(query: String)
    fun clearQuery()
    fun retrySearch()
    fun setEditing(isEditing: Boolean)
}
```

- [ ] Implement explicit-submit search. `onQueryChange()` updates draft text only.
- [ ] Trim submitted queries; blank submits clear loading/error and do not call the repository.
- [ ] Store `submittedQuery` separately from `query` so Retry can run the last committed query.
- [ ] Use a monotonically increasing token or equivalent guard so stale results cannot overwrite
      newer results after rapid submits.
- [ ] Cancel in-flight search from `clearQuery()`.
- [ ] Add session recent queries: max five, newest first, case-insensitive de-dupe.
- [ ] Make `FakeSongRepository.searchSongs()` return matching songs from its `songs` flow and
      expose a call count/failure hook for tests.
- [ ] Preserve `FakeSongRepository.gate` and `throwOnceOnGetSongsByIds`; Favourites and detail
      tests still depend on those hooks.
- [ ] Do not simplify `InertRepositoryFakes.kt` to support search tests. Search tests should need
      `FakeSongRepository`; the Favourites fake hooks from current `main` must remain intact.
- [ ] Remove `searchJob`, `SearchLimit`, `SearchDebounceMs`, `searchQuery`, `searchResults`,
      `onSearchQueryChange()` and `clearSearch()` from `AutomotiveContentViewModel`.
- [ ] Preserve `FavouritesLoadError`, `reportLikedSongsFailure()`, `favouritesError`,
      `favouritesLoading`, and the current `openFavourites()` freeze behavior while removing
      search-owned code.
- [ ] Keep `FavouritesBoundaryTest`, `FavouritesJourneyTest`, `FavouritesSnapshotTest`, and
      `FavouritesTestCase` compiling; do not delete them as part of A6.
- [ ] Update `SongDao.search()` and `FakeSongDao.search()` so album names match too.
- [ ] Add `OfflineSongRepositoryTest` cases for title, artist and album-name matching.

**Verify:**

```bash
./gradlew :automotive:testOemDebugUnitTest :core:data:testDebugUnitTest
```

---

## Task 2: Search sheet navigation and gate semantics

Wire search as the first real system-bar sheet. This task should compile without the final visual
screen polish if needed by temporarily using simple placeholders, but it must make the location
model correct.

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarSystemBar.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/CarUiLocation.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/GateResult.kt`
- Modify: `automotive/src/test/java/com/example/nyasaplayer/auto/ui/navigation/CarRestrictionGateTest.kt` only if comments/assertions need renaming

- [ ] Add `searchViewModel: AutomotiveSearchViewModel = hiltViewModel()` to `AuthenticatedApp`.
- [ ] Collect `searchState` beside `contentState` and `playerState`.
- [ ] Add `var showSearch by rememberSaveable { mutableStateOf(false) }`.
- [ ] Update `carUiLocation()` to take `showSearch: Boolean` and
      `searchTextEntryActive: Boolean`.
- [ ] Set `sheet = if (showSearch) CarSheet.Search else null`.
- [ ] Set `textEntryActive = showSearch && searchState.isEditing`.
- [ ] In the gate's `Denied` branch, close search and call `searchViewModel.setEditing(false)`
      before showing the refusal reason.
- [ ] Clear `showSearch` when switching tabs, opening full player/queue, or following a browse-by
      shortcut.
- [ ] Enable only the search control in `CarSystemBar`. Settings and profile stay disabled and
      visibly unavailable until A7.
- [ ] Update comments in `CarUiLocation.kt` and `GateResult.kt` so they no longer say Search is
      unreachable or owned by `AutomotiveContentViewModel`.

**Verify:**

```bash
./gradlew :automotive:assembleOemDebug :automotive:testOemDebugUnitTest
```

---

## Task 3: Build `CarSearchScreen`

Screen 5 owns the idle and text-entry state. It must be useful while parked and explicitly
non-editable when `NO_KEYBOARD` is active.

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarSearchScreen.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/preview/CarScreenPreviews.kt` only if preview coverage exists nearby

**Suggested signature:**

```kotlin
@Composable
fun CarSearchScreen(
    state: AutomotiveSearchUiState,
    canType: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearQuery: () -> Unit,
    onEditingChange: (Boolean) -> Unit,
    onRecentClick: (String) -> Unit,
    onBrowseGenres: () -> Unit,
    onBrowseLibrary: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] Render a full-screen sheet body, not a dialog/card inside the shell.
- [ ] Add close/back and clear controls with 76dp hit areas.
- [ ] Use a Compose text field with IME Search action when `canType` is true.
- [ ] Do not focus or show a keyboard when `canType` is false.
- [ ] Render the voice-search prompt when `canType` is false. It must not be a clickable no-op.
- [ ] Render recent queries when present and a recent-empty state otherwise.
- [ ] Render browse-by shortcuts: `Songs`, `Genres`, `Albums`, `Artists`, `Playlists`.
- [ ] Route `Genres` to Browse and `Albums`/`Artists`/`Playlists` to Library via callbacks.
- [ ] Keep all text inside fixed/reasonable bounds on 1024x720 and 1920x1080.

**Verify:**

```bash
./gradlew :automotive:assembleOemDebug :automotive:lintOemDebug detekt
```

---

## Task 4: Build `CarSearchResultsScreen` and playback wiring

Screen 6 owns submitted results. The biggest correctness risk is passing a list different from
what the driver can see.

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarSearchResultsScreen.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/preview/CarScreenPreviews.kt` only if needed

**Suggested signature:**

```kotlin
@Composable
fun CarSearchResultsScreen(
    query: String,
    results: List<Song>,
    isLoading: Boolean,
    errorMessage: String?,
    onBackToSearch: () -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
)
```

- [ ] In `AutomotiveApp`, derive `visibleResults` from `searchState.results`.
- [ ] When `restrictions.isDistractionOptimized` is true, cap `visibleResults` with
      `maxCumulativeContentItems`.
- [ ] Render a top-result section from `visibleResults.firstOrNull()`.
- [ ] Render remaining songs with `CarTrackRow`, preserving currently-playing indicators.
- [ ] Tapping any visible song calls the existing playback callback with `visibleResults` and the
      tapped song.
- [ ] Back returns to `CarSearchScreen` with the current query preserved and editing false until
      the field is focused again.
- [ ] Clear closes results, empties query/results/error, and returns to idle search.
- [ ] Loading, no-results and error states must be visible and recoverable.
- [ ] Do not add album/artist result cards in A6.

**Verify:**

```bash
./gradlew :automotive:assembleOemDebug :automotive:testOemDebugUnitTest
```

---

## Task 5: Refresh A6 decisions and contract docs

The scope decision must be visible in the source docs, not just in this plan.

**Files:**
- Modify: `docs/aaos-DESIGN.md`
- Modify: `docs/AAOS_PRD.md`
- Modify: `docs/AAOS_SCREEN_CONTRACT.md`
- Modify: `docs/tickets/A6-search.md`
- Modify: `docs/tickets/T4-automotive-multi-entity-search.md` only if implementation changes the follow-up

- [ ] Confirm D31-D35 in `docs/aaos-DESIGN.md` still match the shipped implementation.
- [ ] Keep Q2 closed in `docs/AAOS_PRD.md`; do not reintroduce "blocked by Q2" wording.
- [ ] Move A6 status from "spec/plan ready" only after implementation and verification land.
- [ ] Keep screen 6 wording scoped to top song result and song rows, not album/artist cards.
- [ ] Confirm T4 still tracks album/artist/playlist result cards and the required data model.
- [ ] Keep the A6 ticket in sync with the implemented status and verification record path.

**Verify:** docs diff review.

---

## Task 6: Full verification and record

**Files:**
- Create: `docs/AAOS_A6_VERIFICATION.md`
- Modify: `docs/AAOS_PRD.md` status table after implementation lands

- [ ] Run `./gradlew :automotive:testOemDebugUnitTest`.
- [ ] Run `./gradlew :core:data:testDebugUnitTest`.
- [ ] Run `./gradlew detekt`.
- [ ] Run `./gradlew :automotive:lintOemDebug`.
- [ ] Run `./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug`.
- [ ] On `AAOS_AOSP_33_userdebug`, run the manual checklist from the spec.
- [ ] Inject driving state with the documented VHAL recipe and verify `DO: true UxR: 255`.
- [ ] Confirm no keyboard appears under `NO_KEYBOARD`.
- [ ] Confirm no manifest contains `RECORD_AUDIO`.
- [ ] Record verified, not verified and observations in `docs/AAOS_A6_VERIFICATION.md`, matching
      the A3-A5 style.

**Definition of done:** all A6 spec §11 items are satisfied, the multi-entity result-card gap is
tracked by T4, and no new unrecorded contract deviation remains.
