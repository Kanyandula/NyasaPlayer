# AAOS T5 + T6 - Navigation State and Content Cap Implementation Plan

> **For agentic workers:** Implement this as a GitHub stacked pull request sequence, one reviewable
> branch per task layer. Each branch must pass its local gate and code review before a PR is
> created for that branch. Do not fold A7 work into this stack.

**Goal:** Fix the two post-A6 structural issues that will otherwise make A7 riskier:

- T6: replace the separate overlay/sheet booleans in `AuthenticatedApp` with one authoritative
  overlay value and one authoritative sheet value.
- T5: replace `BrowseShell`'s unconditional parked content caps with the shared
  `UxRestrictionState.cap()` rule and memoize the derived visible lists.

**Tickets:** `docs/tickets/T6-automotive-navigation-state-encoding.md` and
`docs/tickets/T5-automotive-unconditional-content-cap.md`.

**Spec:** no separate spec. These are cleanup tickets with already-scoped acceptance criteria. This
plan is the execution artifact.

**Tech stack:** Kotlin, Jetpack Compose, Hilt, JUnit 4, Gradle Kotlin DSL, Detekt, Lint.

## Current baseline

Start from `main` after A6 is merged. A6 introduced:

- `CarSheet.Search`
- `AutomotiveSearchViewModel`
- `SearchSheet`
- `UxRestrictionState.cap()`
- `RestrictionCapTest`
- the documented D36-D40 decisions in `docs/aaos-DESIGN.md`

Do not start this stack from the pre-A6 mainline. If `main` does not contain
`docs/AAOS_A6_VERIFICATION.md` and `AutomotiveSearchViewModel.kt`, stop and merge A6 first.

## Why T6 sits below T5

T6 is wanted before A7 because A7 adds Settings and Profile sheets. If T5 sits underneath T6, A7 is
blocked by a parked-list cleanup it does not depend on. Put T6 at the bottom of the stack so it can
merge independently if the content-cap review takes longer.

Stack order:

```text
main
└── ek/aaos-t6-navigation-state              PR 1, base: main
    └── ek/aaos-t5-content-cap               PR 2, base: ek/aaos-t6-navigation-state
        └── ek/aaos-t5-t6-verification-docs  PR 3, base: ek/aaos-t5-content-cap
```

## Stacked PR process

GitHub stacked pull requests are in public preview as of 2026-08-21, so keep commands and UI steps
aligned with current GitHub docs when executing.

Use one of these equivalent creation paths:

- GitHub CLI (`gh stack` is built in; verified present on gh 2.92.0, no extension needed):
  - `gh stack init ek/aaos-t6-navigation-state`
  - commit Task 1.
  - `gh stack add ek/aaos-t5-content-cap`
  - commit Task 2.
  - `gh stack add ek/aaos-t5-t6-verification-docs`
  - commit Task 3.
  - after all local branch review gates pass, run `gh stack submit`.
- GitHub website:
  - create PR 1 with base `main`.
  - create PR 2 with base `ek/aaos-t6-navigation-state` and select the stack option.
  - create PR 3 with base `ek/aaos-t5-content-cap` and add it to the stack.

Rules for this project:

- All stack branches must live in the same repository; do not use cross-fork stack branches.
- Each branch must contain one coherent task layer and only the files listed for that task.
- Before creating a PR for a branch, run the task gate and a code review against that branch's base.
- Fix review findings on the branch where they belong. If a lower-layer fix is made after an upper
  branch exists, cascade the rebase up the stack before continuing.
- Request human review bottom-up: PR 1 first, then PR 2, then PR 3.
- Merge bottom-up unless GitHub's stack UI is used to merge a contiguous approved section.

Pre-PR review command pattern:

```bash
# Layer 1
git diff main...ek/aaos-t6-navigation-state

# Layer 2
git diff ek/aaos-t6-navigation-state...ek/aaos-t5-content-cap

# Layer 3
git diff ek/aaos-t5-content-cap...ek/aaos-t5-t6-verification-docs
```

Run the code-review workflow on that exact diff. Do not create the PR until findings are fixed or
explicitly documented as accepted risk.

## Global constraints

- Max line length 120.
- Trailing commas on call and declaration sites.
- No wildcard imports.
- Detekt `maxIssues: 0`.
- Top-level constants are PascalCase.
- Composables emitting UI take `modifier: Modifier = Modifier` as the first optional parameter.
- Automotive touch targets stay at least 76dp.
- Do not add a navigation library; this remains local `rememberSaveable` state in
  `AuthenticatedApp`.
- Do not build a generic sheet host for two sheets. Keep `SearchSheet`-style per-sheet rendering.
- Do not re-open D36. The rule is already settled: parked lists are not capped; moving lists are.
- Do not change `gate()` semantics. T6 changes how the current location is encoded, not what the
  gate refuses.
- Do not add A7 Settings/Profile UI in this stack.

## Files

**Expected modified files**

| File | Why |
|---|---|
| `automotive/.../ui/AutomotiveApp.kt` | Replace boolean state; use shared caps |
| `automotive/.../navigation/CarUiLocation.kt` | Document the lossy projection |
| `automotive/src/test/.../ui/CarUiLocationTest.kt` | Cover overlay/sheet mapping |
| `automotive/src/test/.../viewmodel/RestrictionCapTest.kt` | Extend cap derivation coverage |
| `docs/aaos-DESIGN.md` | Record any new D41/D42 decisions discovered during pointer-through or cap implementation |
| `docs/tickets/T5-automotive-unconditional-content-cap.md` | Move status forward after implementation/verification |
| `docs/tickets/T6-automotive-navigation-state-encoding.md` | Move status forward after implementation/verification |

**Optional created files**

| File | Why |
|---|---|
| `docs/AAOS_T5_T6_VERIFICATION.md` | Record emulator pointer-through, parked/unparked cap checks and final gates |

Do not touch mobile `:app` UI or playback persistence as part of this stack.

## Task 1: T6 navigation state encoding

**Stack layer:** `ek/aaos-t6-navigation-state`, base `main`.

**Purpose:** Make `AuthenticatedApp` hold one overlay value and one sheet value so the location the
gate sees matches the top surface the driver sees.

### Design

Keep the current `currentScreen` and `drillDown` ownership. Replace only transient surfaces.

**The two overlays stack; they are not alternatives.** Today `showFullPlayer` and `showQueue` are
both true while the queue is open above the player, which is why dismissing the queue reveals the
player still underneath (`AutomotiveApp.kt:204` renders the player, `:322` renders the queue over
it). Collapsing them into one nullable value would make closing the queue drop the driver onto the
browse shell with the player gone — a visible regression on a path A5 device-verified. Model the
overlays as a stack and the sheet as a single value:

```kotlin
// Max depth 2 today: [FullPlayer], [Queue], or [FullPlayer, Queue]. last() is the top surface.
val overlays = rememberSaveable(
    saver = listSaver(
        save = { it.map(CarOverlay::name) },
        restore = { it.map(CarOverlay::valueOf).toMutableStateList() },
    ),
) { mutableStateListOf<CarOverlay>() }

var sheet by rememberSaveable { mutableStateOf<CarSheet?>(null) }
```

A saver is required: the default `autoSaver` cannot put a `List` in a `Bundle`. Verify restore
across process death as part of Task 3 rather than assuming it.

Expected rules:

- `overlays.lastOrNull()` is the top surface, and is what `carUiLocation()` reports as `overlay`.
- `CarOverlay.FullPlayer` on top renders `CarFullPlayerScreen`; `CarOverlay.Queue` on top renders
  `CarQueueScreen` above whatever is beneath it.
- Opening the full player replaces the stack with `[FullPlayer]`.
- Opening the queue **pushes** — from the full player that gives `[FullPlayer, Queue]`, from the
  mini player `[Queue]`.
- Closing the queue **pops**, so it returns to the full player when that is what it covered, and to
  the browse shell otherwise. This is the behaviour that must not regress.
- Collapsing the full player clears the stack.
- `sheet == CarSheet.Search` renders `SearchSheet`.
- Opening an overlay clears the sheet; opening a sheet clears the overlay stack.
- Selecting a tab clears `drillDown`, the overlay stack and the sheet.
- Gate denial clears the overlay stack, the sheet and `drillDown`, then applies
  `result.evictTo.tab`.

The teardown win T6 exists for is the **sheet**: `closeSearch()` is threaded through five call
sites today and A7's Settings and Profile would add roughly ten more. The overlay change is the
smaller half — it fixes the gate reporting `FullPlayer` while the queue is on top — so if the stack
shape proves awkward in review, keep the two booleans and derive `overlay` with a documented
Queue-over-FullPlayer precedence rather than dropping the nesting.

Do not assign a local navigation state from `result.evictTo`. `CarUiLocation` contains
`drillDepth: Int`, not the original `CarDestination`, so it cannot reconstruct `drillDown`.
Document this on `CarUiLocation.tabRoot()`.

### Pointer-through decision

Before changing production navigation, answer the sheet pointer question on the emulator:

- Open Queue, tap empty areas where the rail or mini-player exists underneath.
- Open Search idle/results, tap empty areas where the rail or mini-player exists underneath.
- Record whether touches reach the shell behind the full-screen surface.

If touches leak through, add a semantics-neutral pointer-consuming root to full-screen surfaces
rather than a no-op `clickable`. Do not create a control that reads as interactive just to swallow
events.

### Checklist

- [ ] Confirm local `main` contains A6 and is clean.
- [ ] Start the bottom stack branch: `gh stack init ek/aaos-t6-navigation-state` or an equivalent
      same-repository branch.
- [ ] Run the pointer-through check before editing navigation code.
- [ ] Replace `showFullPlayer` and `showQueue` with the `overlays` stack, keeping push/pop so
      closing the queue still reveals the full player underneath.
- [ ] Replace `showSearch` with `sheet: CarSheet?`.
- [ ] Delete `closeSearch()` and replace it with focused helpers such as `openOverlay()`,
      `openSheet()`, `closeOverlay()`, `closeSheet()` and `clearTransientSurfaces()`.
- [ ] Update `selectTab` so one path clears `drillDown`, `overlay` and `sheet`.
- [ ] Update gate denial so it no longer lists every old boolean. It should clear transient
      surfaces and retain only `result.evictTo.tab`.
- [ ] Update `CarFullPlayerScreen.onQueueClick` so opening Queue makes
      `CarUiLocation.overlay == CarOverlay.Queue`, not `FullPlayer`.
- [ ] Update `carUiLocation()` to accept `overlay: CarOverlay?` (the top of the stack) and
      `sheet: CarSheet?` directly.
- [ ] Keep `textEntryActive = sheet == CarSheet.Search && searchState.isEditing`.
- [ ] Update `CarUiLocationTest` for direct overlay/sheet inputs, including Queue opened from Full
      Player and stale editing after Search closes.
- [ ] Cover the pop path: closing the queue from `[FullPlayer, Queue]` leaves `FullPlayer` on top,
      and from `[Queue]` leaves nothing. Mutation-check it — collapsing the stack to a single value
      must fail a named test.
- [ ] Update comments in `CarUiLocation.kt` and `GateResult.kt` only where the refactor makes them
      stale.
- [ ] Run `./gradlew :automotive:testOemDebugUnitTest`.
- [ ] Run code review against `main...ek/aaos-t6-navigation-state` and fix findings before PR
      creation.

**Acceptance criteria**

- Given Queue is opened from Full Player, `CarUiLocation.overlay` reports `Queue`.
- Given Queue is then closed, the full player is still showing — the driver is not dropped onto the
  browse shell.
- Given Search is closed while `AutomotiveSearchViewModel.isEditing` is stale, location text entry
  is false.
- Given the gate denies a restricted location, the caller clears transient surfaces without a
  per-boolean teardown list.
- Given A7 adds Settings/Profile later, it needs an enum case/render branch, not a new navigate-away
  clear call at every surface transition.

## Task 2: T5 parked content cap and memoization

**Stack layer:** `ek/aaos-t5-content-cap`, base `ek/aaos-t6-navigation-state`.

**Purpose:** Apply the D36 cap rule consistently to Browse, Library, detail and Favourites lists:
uncapped while parked, capped only when distraction optimization is required.

### Design

`BrowseShell` should carry the full `UxRestrictionState`, not just
`maxCumulativeContentItems`. Every list that currently uses `.take(maxItems)` should be derived
through `playerState.restrictions.cap(...)` and remembered with the source list plus restrictions
as keys.

Expected changes:

- Home:
  - `recentlyPlayed`
  - `popularSongs`
- Browse:
  - `genres`
- Library root:
  - `recentlyPlayed`
  - `playlists`
  - `albums`
  - `favoriteArtists`
- Library artist drill-down:
  - filtered liked songs
- Detail route:
  - album/playlist tracks
- Favourites:
  - `contentState.favourites ?: contentState.likedSongs`

Prefer a small local helper if it keeps the call sites readable, for example a
`rememberVisibleItems(items, restrictions)` composable helper or a pure derivation helper with
focused tests. Do not move the cap into `AutomotiveContentViewModel` unless the Compose call-site
version becomes meaningfully harder to read or test.

### Checklist

- [ ] Add the second stack layer: `gh stack add ek/aaos-t5-content-cap` or create a branch based on
      `ek/aaos-t6-navigation-state`.
- [ ] Replace all unconditional `BrowseShell` `.take(maxItems)` call sites with
      `restrictions.cap(...)`.
- [ ] Memoize the derived lists so the 500ms playback position tick does not allocate fresh visible
      lists for unchanged content.
- [ ] Update `DetailRoute` to take `UxRestrictionState` or another explicit cap policy instead of a
      raw `maxItems` integer.
- [ ] Preserve current driving behavior: when `isDistractionOptimized` is true, visible lists remain
      capped to `maxCumulativeContentItems`.
- [ ] Preserve parked behavior: when `isDistractionOptimized` is false, lists longer than
      `maxCumulativeContentItems` remain complete.
- [ ] Extend `RestrictionCapTest` or add focused helper tests for at least Home, Library detail and
      Favourites derivations.
- [ ] Manually smoke the AAOS emulator with a library larger than the reported cap: parked lists
      should scroll past the cap; driving lists should stop at the cap.
- [ ] While music is playing, observe Home, Library and Browse scroll performance after removing the
      parked caps. If a screen becomes visibly janky, stop and document the finding rather than
      reintroducing unconditional caps.
- [ ] Run `./gradlew :automotive:testOemDebugUnitTest`.
- [ ] Run code review against
      `ek/aaos-t6-navigation-state...ek/aaos-t5-content-cap` and fix findings before PR creation.

**Acceptance criteria**

- Given the vehicle is parked and the platform still reports a cap, screen lists are not truncated.
- Given the vehicle is moving, the same lists are capped exactly to the platform count.
- Given playback position ticks every 500ms, unchanged content lists are not reallocated at every
  tick by the T5 derivation itself.
- Given detail tracks and Favourites are opened parked, their visible lists follow the same D36
  rule as Home/Library/Browse.

## Task 3: Verification, docs and ticket status

**Stack layer:** `ek/aaos-t5-t6-verification-docs`, base `ek/aaos-t5-content-cap`.

**Purpose:** Record what was proven and move T5/T6 out of "Ready to spec" only after implementation
and verification are real.

### Checklist

- [ ] Add the third stack layer: `gh stack add ek/aaos-t5-t6-verification-docs` or create a branch
      based on `ek/aaos-t5-content-cap`.
- [ ] Create `docs/AAOS_T5_T6_VERIFICATION.md` if manual checks were needed.
- [ ] Record the pointer-through result and any chosen mitigation.
- [ ] Verify the overlay stack survives process death: open the queue over the full player, kill the
      process, and confirm the restored surface matches (see the A4/A5 note that `am kill` is
      refused while playback holds the foreground service — stop playback first and check the PID
      actually changed).
- [ ] Record parked and driving content-cap checks, including the observed cap value and at least
      one list with more rows than the cap.
- [ ] Update `docs/aaos-DESIGN.md` only for new decisions. Do not rewrite D36-D40 unless they are
      stale.
- [ ] Update T5 and T6 ticket statuses to implemented/verified only after the gates pass.
- [ ] Run the full local gate:

```bash
./gradlew :automotive:testOemDebugUnitTest detekt \
  :automotive:lintOemDebug \
  :automotive:assembleOemDebug \
  :automotive:assemblePlaystoreDebug
```

- [ ] Run code review against
      `ek/aaos-t5-content-cap...ek/aaos-t5-t6-verification-docs` and fix findings before PR
      creation.
- [ ] Create or update the stacked PRs only after Tasks 1-3 pass their pre-PR review gates.
- [ ] Request review bottom-up: T6 first, then T5, then verification/docs.

**Acceptance criteria**

- `docs/tickets/T5-automotive-unconditional-content-cap.md` points to the verification record and
  no longer says only "Ready to spec" after implementation lands.
- `docs/tickets/T6-automotive-navigation-state-encoding.md` points to the verification record and
  no longer says only "Ready to spec" after implementation lands.
- The top stack branch has a recorded full gate and no unrecorded manual carve-outs.

## Definition of done

- T6 acceptance criteria are satisfied and reviewed in the bottom stack PR.
- T5 acceptance criteria are satisfied and reviewed in the second stack PR.
- The verification/docs branch records the manual checks, gate output and any accepted carve-outs.
- Every branch receives code review before PR creation and normal GitHub review after PR creation.
- The stack can merge bottom-up without requiring A7 changes.
