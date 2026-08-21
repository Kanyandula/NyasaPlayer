# AAOS T4 - Multi-Entity Search Results Implementation Plan

> **For agentic workers:** This plan is scoped to design-ready implementation. Do not reopen A6
> text entry, do not add voice capture, and do not fake album/artist/playlist cards from already
> collected UI lists. Keep each task reviewable and verify before moving to the next layer.

**Goal:** Replace song-only submitted automotive search results with typed, ranked sections for
songs, albums, artists and playlists, while keeping media-session voice search song-compatible.

**Ticket:** `docs/tickets/T4-automotive-multi-entity-search.md`

**Spec:** `docs/superpowers/specs/2026-08-21-aaos-t4-multi-entity-search-design.md`

**Verification command:** `./gradlew :automotive:testOemDebugUnitTest :core:data:testDebugUnitTest`

**Tech stack:** Kotlin, Jetpack Compose, Room, Firebase Firestore, Hilt, Media3, JUnit 4,
kotlinx-coroutines-test, Robolectric Compose tests if T1 is on the branch.

## Current baseline

Start from `main` after A6 is merged. Prefer a baseline that also contains T1 Compose test tooling;
T4 adds result UI and should not rely only on manual screenshots for section visibility and tap
routing.

The baseline must already contain:

- `AutomotiveSearchViewModel`
- `CarSearchScreen`
- `CarSearchResultsScreen`
- `UxRestrictionState.cap()`
- `CarDestination.Album` and `CarDestination.Playlist`
- A6 search docs and `docs/AAOS_A6_VERIFICATION.md`

If any of those are missing, stop and merge A6 first.

## Design decisions to preserve

- Search still runs only on explicit submit.
- Blank, stale, failed and retried searches keep the A6 behavior.
- Use repository search primitives plus an automotive coordinator.
- The typed model includes songs, albums, artists and playlists.
- T4 adds a general catalogue artist detail destination; it does not reuse the liked-songs artist
  screen.
- Media-session search remains song-only, but the song ordering must match the launcher song
  section.
- The driving cap is cumulative across the rendered result stream.

## Global constraints

- Max line length 120.
- Trailing commas on call and declaration sites.
- No wildcard imports.
- Detekt `maxIssues: 0`.
- Top-level constants are PascalCase.
- Composables emitting UI take `modifier: Modifier = Modifier` as the first optional parameter.
- Automotive touch targets stay at least 76dp.
- Do not add `RECORD_AUDIO`, `RecognizerIntent` or in-app audio capture.
- Do not introduce a navigation library.
- Do not move mobile `:app` search unless a shared interface signature forces a compile fix.
- Do not add a full-text search dependency.

## File plan

**Create**

| File | Responsibility |
|---|---|
| `automotive/.../auto/search/AutomotiveSearchResult.kt` | Typed result model, section container and ranking helpers |
| `automotive/.../auto/search/AutomotiveCatalogSearch.kt` | Repository-backed typed search coordinator |
| `automotive/src/test/.../auto/search/AutomotiveCatalogSearchTest.kt` | Ranking and sectioning tests |
| ~~`automotive/src/main/.../auto/ui/screens/CarArtistScreen.kt`~~ | Landed as a wrapper in `CarDetailScreen.kt` — see Task 4 |
| `automotive/src/test/.../ui/screens/CarSearchResultsScreenTest.kt` | Multi-section rendering and cap tests |
| `core/data/src/test/.../offline/OfflineAlbumRepositoryTest.kt` | Album search semantics |
| `core/data/src/test/.../offline/OfflineArtistRepositoryTest.kt` | Artist search semantics |

**Modify**

| File | Change |
|---|---|
| `core/data/.../api/AlbumRepository.kt` | Add `searchAlbums(query, limit)` |
| `core/data/.../api/ArtistRepository.kt` | Add `searchArtists(query, limit)` |
| `core/data/.../api/PlaylistRepository.kt` | Add `searchPlaylists(userId, query, limit)` |
| `core/data/.../local/dao/AlbumDao.kt` | Add deterministic album search query |
| `core/data/.../local/dao/ArtistDao.kt` | Add deterministic artist search query |
| `core/data/.../offline/OfflineAlbumRepository.kt` | Implement album search |
| `core/data/.../offline/OfflineArtistRepository.kt` | Implement artist search |
| `core/data/.../FirebasePlaylistRepository.kt` | Implement one-shot user playlist search |
| `core/data/src/test/.../fake/FakeAlbumDao.kt` | Add album search fake |
| `automotive/src/test/.../fake/FakeAlbumRepository.kt` | Add album search fake |
| `automotive/src/test/.../fake/FakePlaylistRepository.kt` | Add playlist search fake |
| `core/data/src/test/.../fake/FakeArtistRepository.kt` | Add artist search fake if used by tests |
| `automotive/.../viewmodel/AutomotiveSearchViewModel.kt` | Replace song-only result state with typed results |
| `automotive/src/test/.../viewmodel/AutomotiveSearchViewModelTest.kt` | Preserve A6 behavior with typed state |
| `automotive/.../viewmodel/AutomotiveContentViewModel.kt` | Load general artist detail |
| `automotive/src/test/.../viewmodel/DetailLoadingTest.kt` | Add catalogue artist detail cases |
| `automotive/.../ui/navigation/CarDestination.kt` | Add distinct catalogue artist destination |
| `automotive/.../ui/AutomotiveApp.kt` | Route result taps and detail rendering |
| `automotive/.../ui/screens/CarSearchResultsScreen.kt` | Render featured card and typed sections |
| `core/playback/.../MediaBrowseTree.kt` | Keep/search-test song parity if song ordering changes |
| `core/playback/src/test/.../MediaBrowseTreeTest.kt` | Prove media-session song ordering parity |
| `docs/AAOS_SCREEN_CONTRACT.md` | Update screen 6 wording after cards ship |
| `docs/aaos-DESIGN.md` | Record final T4 decisions if implementation revises details |
| `docs/tickets/T4-automotive-multi-entity-search.md` | Move status after implementation/verification |

## Task 0: Baseline and branch

**Purpose:** Start from the right code shape and avoid carrying a stale A6/T1 branch forward.

- [x] Confirm `main` contains A6 search and the T5/T6 navigation/cap fixes.
- [x] Prefer a branch that contains T1 Compose test tooling.
- [x] Start a fresh T4 branch from the intended baseline.
- [x] Run `git status --short` and identify unrelated dirty files before editing.
- [x] Read the T4 spec and ticket in full.

**Acceptance criteria:** the implementation branch starts from the current AAOS search baseline and
does not include unrelated work.

## Task 1: Add repository search primitives

**Purpose:** Make albums, artists and playlists searchable from their own data sources.

- [x] Add `searchAlbums(query: String, limit: Int): List<Album>` to `AlbumRepository`.
- [x] Add `searchArtists(query: String, limit: Int): List<Artist>` to `ArtistRepository`.
- [x] Add `searchPlaylists(userId: String, query: String, limit: Int): List<Playlist>` to
      `PlaylistRepository`.
- [x] Add Room DAO search methods for albums and artists.
- [x] Order DAO results by match quality, popularity, case-insensitive title/name and stable id.
- [x] Implement offline album and artist repository methods.
- [x] Implement playlist search in `FirebasePlaylistRepository` as a repository-owned one-shot
      search, not by reading `AutomotiveContentState.playlists`.
- [x] Update all fakes that implement the changed interfaces.
- [x] Add or extend core data tests for album primary match, album artist match, artist name match,
      ordering and limit behavior. `CatalogSearchDaoTest` runs them against an in-memory Room
      database, because a fake that re-implements the SQL would only agree with itself.
- [ ] Add playlist fake coverage in automotive tests if Firebase cannot be tested cleanly on JVM.
      Deferred to Task 2: the fake is updated, but nothing calls `searchPlaylists` until the
      coordinator exists. `FirebasePlaylistRepository.searchPlaylists` itself stays JVM-untestable.

**Technical notes**

For playlists, Firestore has no current normalized contains index in this model. T4 may fetch the
user's playlist collection through the repository and filter names in memory there. That is
acceptable because the filtering happens inside the repository's own source read, not inside a UI
collector that may not have emitted.

**Acceptance criteria:** core data search APIs compile, are deterministic, and do not depend on
automotive UI state.

## Task 2: Add the automotive search coordinator

**Purpose:** Give `AutomotiveSearchViewModel` one typed search dependency instead of four
repositories and ranking code.

- [x] Create the typed result model and `AutomotiveSearchResults` container.
- [x] Create `AutomotiveCatalogSearch` or equivalent.
- [x] Inject `SongRepository`, `AlbumRepository`, `ArtistRepository`, `PlaylistRepository` and
      `AuthRepository`.
- [x] Trim and normalize committed queries once in the coordinator.
- [x] Run song, album, artist and playlist searches with per-type limits.
- [x] Treat missing signed-in user id as an empty playlist section, not as a whole-search failure.
- [x] Rank results according to the spec.
- [x] Pick `featured` from the ranked union and remove it from its own section.
- [x] Return empty typed sections for no results.
- [x] Add tests for multi-type matches, featured de-duplication, exact-vs-secondary ranking, no
      user id playlist behavior, repository failure behavior and stable ordering.
      `AutomotiveCatalogSearchTest`, 8 tests; four mutations run against them, all killed by name.

**Acceptance criteria:** a query can return all four result types with stable type preservation and
deterministic ordering.

## Task 3: Update `AutomotiveSearchViewModel`

**Purpose:** Preserve A6 state-machine behavior while replacing the song-only result list.

- [x] Inject the automotive search coordinator instead of `SongRepository`.
- [x] Replace `results: List<Song>` with typed `AutomotiveSearchResults` or equivalent.
- [x] Keep `query`, `submittedQuery`, `recentQueries`, `isLoading`, `errorMessage` and
      `isEditing` semantics unchanged.
- [x] Keep the stale-token guard.
- [x] Keep `clearQuery()` and `backToSearch()` cancellation semantics.
- [x] Keep failed search behavior all-or-error; do not show partial stale rows.
- [x] Update `AutomotiveSearchViewModelTest` rather than weakening it.
- [x] Add tests that prove typed results survive retry and stale-result rejection.

**Acceptance criteria:** every A6 ViewModel behavior still passes with typed result state.

## Task 4: Add general artist detail navigation

**Purpose:** Give artist result cards an honest destination.

- [x] Add a distinct destination such as `CarDestination.CatalogArtist(artistId: String)`.
- [x] Leave the existing liked-songs artist route intact.
- [x] Update comments that currently describe only album/playlist detail.
- [x] Add `AutomotiveContentViewModel` loading for catalogue artist metadata and songs.
- [x] Load artist songs from `SongRepository.getSongsByArtist(artistId)`.
- [x] Build a general `CarArtistScreen` or adapt the existing detail body without favourite
      toggles. It is a wrapper over the shared `CarDetailBody` in `CarDetailScreen.kt`, beside its
      album and playlist siblings, rather than the separate `CarArtistScreen.kt` the file plan
      named — the body already renders tracks without a like affordance.
- [x] Keep drill depth at 1.
- [x] Add tests for artist found, missing artist, empty artist tracks and retry.

**Acceptance criteria:** tapping an artist search result opens a catalogue artist detail screen,
not the liked-songs artist screen.

## Task 5: Render multi-entity results

**Purpose:** Replace the song-only results screen with typed featured and section cards.

- [ ] Update `SearchSheet` to derive visible typed results from `state.submittedQuery`.
- [ ] Apply the driving cap to the flattened rendered result stream, then re-section.
- [ ] Render a featured card when present.
- [ ] Render sections in fixed order: Songs, Albums, Artists, Playlists.
- [ ] Omit empty sections.
- [ ] Keep loading, no-results, error, retry, clear and back behavior.
- [ ] Use existing car primitives and 76dp touch targets.
- [ ] For song cards, pass the visible song-result queue to playback.
- [ ] For album, artist and playlist cards, close search and open the corresponding detail route.
- [ ] Add Compose tests for all section labels, empty sections, featured de-duplication, cap
      behavior and tap callbacks if the T1 harness is present.

**Acceptance criteria:** multi-entity results render and route by type, and the visible/tappable
list is exactly the capped rendered list.

## Task 6: Preserve media-session parity

**Purpose:** Keep Android Auto / Assistant search semantics documented and tested.

- [ ] Keep `PlaybackService.onSearch` and `onGetSearchResult` song-only for T4.
- [ ] Ensure `MediaBrowseTree.search()` still uses `SongRepository.searchSongs()`.
- [ ] If song search ordering changes in Task 1, update `MediaBrowseTreeTest` for that ordering.
- [ ] Add a short code comment or doc note explaining that non-song cards are custom-launcher
      enrichment.
- [ ] Do not return non-playable album, artist or playlist cards from media-session search in T4.

**Acceptance criteria:** the media-session search path and custom launcher song section have the
same song semantics, with documented launcher-only enrichment for non-song cards.

## Task 7: Documentation and final gate

**Purpose:** Leave the repo in an implementation-complete state for review.

- [ ] Update `docs/AAOS_SCREEN_CONTRACT.md` screen 6 from deferred wording to shipped
      multi-entity wording.
- [ ] Update `docs/aaos-DESIGN.md` with final T4 decisions if implementation changed any spec
      detail.
- [ ] Update `docs/tickets/T4-automotive-multi-entity-search.md` status after verification.
- [ ] Run:

```bash
./gradlew :automotive:testOemDebugUnitTest :core:data:testDebugUnitTest
```

- [ ] Run any broader local gate the branch normally requires, such as detekt/lint/assemble.
- [ ] Record any device-only verification carve-outs if UI behavior cannot be covered by JVM
      Compose tests.

**Acceptance criteria:** implementation, tests and docs agree on the same T4 behavior.

## Definition of done

- T4 search results are typed and multi-entity.
- Album, artist and playlist results come from repository search APIs.
- Artist cards open a real catalogue artist detail destination.
- Driving caps are cumulative across the rendered result stream.
- Song playback starts from visible song results only.
- Media-session search remains song-compatible with the launcher song section.
- `./gradlew :automotive:testOemDebugUnitTest :core:data:testDebugUnitTest` passes.
