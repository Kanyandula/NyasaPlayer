# Review brief for Codex — AAOS A3 spec

**Review target:** `docs/superpowers/specs/2026-08-09-aaos-browse-library-detail-design.md`
**Branch:** `ek/aaos-a2-impl` (A2 merged to `main` as PR #16; this spec is uncommitted)
**Prior slices:** A1 spec+plan and A2 spec+plan are in the same two directories and were both
reviewed by you.

---

## What this is

The **spec** for slice A3. No implementation code has been written and no plan exists yet —
the plan is written after this spec passes review, which is the point of reviewing now.

A3 covers four screens from `docs/AAOS_SCREEN_CONTRACT.md`: 4 `CarBrowseScreen` (rebuild),
7 `CarLibraryScreen` (rebuild), 10 `CarPlaylistScreen` (new), 11 `CarAlbumScreen` (new).

## Project context

NyasaPlayer is an Android/Kotlin/Compose music app. The `:automotive` module ships a custom
AAOS launcher — 8 Compose screens today — alongside the OEM media template served by
`:core:playback`'s `PlaybackService`. The custom launcher is the product for this work.

Slices so far:

- **A1** — gold design tokens, component primitives, `oem`/`playstore` flavors, and the
  restriction layer: `CarUxRestrictionsHandler`, `UxRestrictionState`, `CarUiLocation`,
  `gate()`, eviction.
- **A2** — the chrome contract. `CarSystemBar` + `CarNavRail` + `CarMiniPlayer` render
  identically on every screen; `CarHomeScreen` rebuilt; parked-only ambient motion.
- **A3** — this spec.

## Decisions already taken

Challenge the reasoning if you think it is wrong, but these were decided with the user and are
settled rather than open. All are in §8 of the spec with full rationale.

- **D8 — Library is horizontal carousels, so detail screens sit at drill depth 1.** The
  alternative (category tiles → list screen → detail) puts detail at depth 2, which is refused
  under any realistic `maxContentDepth` while driving.
- **D10 — Browse's existing search field is deleted in A3, not carried until A6.** Search is
  unreachable between A3 and A6. The system-bar search button already renders disabled from
  A2's D3.
- **D15 / §3.1 — `PlaylistRepository` is wired read-only.** Its four write methods stay unused.
- **§2.2 — the drill-down is one `CarDestination?`, not a back stack and not three nullables.**

## Environment facts — verified, not assumed

| | |
|---|---|
| Kotlin | 2.0.21 · AGP 8.8.0 · compileSdk/minSdk 35/29 · JVM target 11 |
| Detekt | `maxIssues: 0`, scans `*/src/main/java` only |
| `:automotive` test source set | exists — `UxFlagsTest`, `CarRestrictionGateTest`, `DecorativeMotionTest` |
| kotlin-parcelize | **not** applied to `:automotive` (no `parcelize` in its `build.gradle.kts`) |
| `CarScreen`, `FavoriteArtist` | both already `java.io.Serializable`; that is the precedent §2.2 follows |
| `SongRepository.getSongsByIds` | `suspend fun getSongsByIds(ids: List<String>): List<Song>` — `OfflineSongRepository` **does** preserve `ids` order (`OfflineSongRepository.kt:21-28`); an earlier version of this brief claimed otherwise and was wrong |
| `PlaylistRepository.getPlaylists` | `fun getPlaylists(userId: String): Flow<List<Playlist>>` |
| `Playlist` | `id`, `name`, `songIds`, `createdAt`, `updatedAt` — **no cover image field** |
| `Genre` | `id`, `name`, `color`, `imageUrl`, `popularity`, `songIds` — **no mood/category field** |
| `gate()` depth rule | `location.drillDepth > state.maxContentDepth -> ReasonDepth`; A3 adds no gate rules |
| Screen sizes today | `CarBrowseScreen.kt` 548 lines, `CarLibraryScreen.kt` 525, `AutomotiveContentViewModel.kt` 268, `AutomotiveApp.kt` 433 |

Signatures quoted in the spec were copied from the files, not reconstructed. If any disagree
with the repo, the repo wins and I want to know.

## Where I am least confident — highest-value targets, ranked

1. **§2.2, `rememberSaveable` with a `Serializable` sealed interface.** Does
   `rememberSaveable { mutableStateOf<CarDestination?>(null) }` actually round-trip through
   Compose's `autoSaver` and the Bundle? `CarScreen` is a `Serializable` *enum* and
   `FavoriteArtist` a `Serializable` *data class*, both of which work today — but I have not
   verified a sealed interface whose implementations are `Serializable` data classes, nor
   whether the nullable type argument changes anything. If this does not survive process death,
   the whole navigation section needs a custom `Saver` and the spec is wrong.

2. **§3.2, `openDetail()` with a no-op branch.** `openDetail(destination)` handles Album and
   Playlist by loading tracks, and for `CarDestination.Artist` sets `detail = null` and returns,
   because artist tracks must stay a live filter over the `likedSongs` flow rather than a frozen
   snapshot. One entry point with a documented early return, versus two functions and a type
   switch at the call site. Is the early return a smell that will confuse the implementer?

3. **§3.2, cancellation and ordering.** `detailJob` is cancelled on every `openDetail`/
   `closeDetail`. Is there a path where a cancelled load still writes `detail`, or where
   `closeDetail()` racing a fast `openDetail()` leaves a detail screen rendering the previous
   destination's tracks? The shell drives both from a single
   `LaunchedEffect(drillDown)`.

4. **§4.2, the Favourites row.** The contract lists Favourites as a Library category row, but
   Favourites is also a rail tab that renders exactly that list. The spec ships **one card
   showing a liked count** that selects the tab, rather than a carousel of liked songs.
   Defensible reading of the contract, or a dodge?

5. **§3.3, the `orderByIds` extraction.** Four call sites, `internal`, in the viewmodel package,
   tested from `automotive/src/test`. Detekt does not scan test sources. Is `internal` in a
   viewmodel package the right home, or should it go somewhere in `:core:common` where
   `:app` could use it too? (`:app` has the same pattern in its own ViewModels.)

6. **§4.1 and §6, deleting `browseCategories`.** Browse currently renders a hardcoded category
   grid matched to Firestore genres by name, with a silent `return@launch` on no match. I claim
   driving the grid from `genres` removes both the placeholder data and the silent no-op. Is
   there a case where `genres` is legitimately empty in production and the screen should fall
   back to something rather than showing an empty state?

7. **Scope.** Four screens, a navigation change, a new repository wiring, a new shared component
   and an extraction in one slice. A2 was nine tasks. Is A3 too big to land as one branch, and
   if so, where is the natural split?

## Out of scope

- The design system's visual choices, and the 20-screen contract itself — both are settled
  inputs, not deliverables of this slice.
- A4–A8 screens. A3 only has to leave them reachable.
- `:app`, `:core:data`, `:core:playback` — except for injecting the existing
  `PlaylistRepository` into the car ViewModel, which adds no new interface.
- The mobile brand migration (Project B).

## Verdict needed

Is this spec safe to turn into an implementation plan, or does something need fixing first?

Specifically: any claim about the repo that is false, any API used in a way that will not
compile or will not survive process death, any contract requirement in
`AAOS_SCREEN_CONTRACT.md` screens 4/7/10/11 with no covering section, and any decision in §8
whose stated rationale does not hold up.