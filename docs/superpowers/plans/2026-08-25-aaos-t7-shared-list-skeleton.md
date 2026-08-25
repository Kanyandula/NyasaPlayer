# AAOS T7 - Shared List Skeleton Implementation Plan

> **For agentic workers:** This is a pure extraction. The rendered output must not change on any
> screen. No shimmer, no new loading logic, no touching when a screen decides to show a skeleton.

**Goal:** One `CarRowSkeleton` in `ui/components/`, replacing four hand-written copies of the same
static row placeholder, so the next screen to need one cannot invent a fifth with its own
dimensions.

**Ticket:** `docs/tickets/T7-automotive-shared-list-skeleton.md`

**Verification command:** `./gradlew :automotive:assembleOemDebug detekt`

**External review:** `codex exec`, 2026-08-25 — no blockers, four should-fixes and two nits, all
folded in below.

**Broader gate:** `./gradlew :automotive:testOemDebugUnitTest detekt :automotive:lintOemDebug
:automotive:assembleOemDebug :automotive:assemblePlaystoreDebug`

## What is actually there

Six skeletons, not four. Read before writing:

| Composable | Shape | Rows | Row height | Spacing | Outer padding |
|---|---|---|---|---|---|
| `HomeSkeleton` | row list | 4 | `SkeletonRowHeight` 80dp | `SkeletonSpacing` 12dp | vertical 24dp |
| `FavouritesSkeleton` | row list | 4 | `CarListRowHeight` 80dp | `SectionSpacing` 16dp | vertical 24dp |
| `DetailSkeleton` | hero + row list | 4 | `SkeletonRowHeight` 80dp | `HeroSpacing` 24dp | vertical 24dp |
| `ResultsSkeleton` | row list | 4 | `CarListRowHeight` 80dp | `RowSpacing` 12dp | none |
| `LibrarySkeleton` | 2 rows of 4 `CarContentCardSize` cards | — | 180dp square | `CardSpacing` 24dp | vertical 24dp |
| `BrowseSkeleton` | 1 row of 3 weighted squares | — | aspect ratio 1:1 | `GridSpacing` 24dp | vertical 24dp, end 16dp |

The first four are the ticket's four. The last two are card grids — a different shape, not a
different copy of this one.

## Decisions

### D-T7.1: The row height moves into the component, because all four already agree

Every one of the four is **80dp**: two spell it `CarListRowHeight`, two declare a private
`SkeletonRowHeight = 80.dp` that shadows it. Fixing the height inside the component is therefore a
no-op on screen and deletes both redundant constants, which is exactly the drift the ticket was
filed for. If a screen ever needs a different row height, that is the moment to add the parameter —
not now, on speculation.

### D-T7.2: Spacing is a parameter; outer padding stays with the caller

The four spacings genuinely differ — 12, 16, 24, 12 — and each matches its screen's own rhythm, so
spacing is a required parameter. Outer padding differs too (three pad 24dp vertically, Results pads
nothing) but it belongs on the `modifier` the caller already passes; a `contentPadding` parameter
would be a second way to say the same thing.

### D-T7.3: No `rows` parameter, despite the ticket proposing one

The ticket suggests `CarRowSkeleton(rows: Int = 4, …)`. All four call sites use 4, so the parameter
would have exactly one value at every call site on the day it ships. Hard-code it and add the
parameter when a caller needs a different count.

Expected signature:

```kotlin
@Composable
fun CarRowSkeleton(spacing: Dp, modifier: Modifier = Modifier)
```

`modifier` follows the required parameter as the first optional one, per the compose-rules detekt
config.

### D-T7.4: `LibrarySkeleton` and `BrowseSkeleton` stay as they are

They draw aspect-ratio cards in a grid, not full-width rows. Folding them in would mean a component
with a shape switch, which is more complexity than the duplication costs. If the grids drift the way
the rows did, that is a separate ticket with a `CarCardGridSkeleton` in it.

### D-T7.5: Its own file, `ui/components/CarRowSkeleton.kt`

Every other car component has one — `CarEmptyState.kt`, `CarTrackRow.kt`. `CarPrimitives.kt` holds
`Modifier` extensions, not composables.

## Task 1: Add the component

- [x] Create `automotive/.../ui/components/CarRowSkeleton.kt`.
- [x] `Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing))` over
      `SkeletonRowCount` × `Box(fillMaxWidth, height CarListRowHeight, clip CarCardCornerRadius,
      background CarRaised)`.
- [x] Keep a private `SkeletonRowCount = 4` rather than a bare `repeat(4)`. `MagicNumber` is active
      and permits only -1, 0, 1, 2; the existing screens each dodge it with the same constant, which
      is why four of them exist.
- [x] Move `HomeSkeleton`'s KDoc onto the component — it already carries the rule and its reason
      ("static placeholders, no shimmer… the ambient layer is the app's only decorative motion, and
      it is gated on vehicle state"). This is a move, not a new sentence: today one of four copies
      explains itself and the other three are silent.

**Acceptance criteria:** the component renders what the four copies render today.

## Task 2: Collapse the four call sites

- [x] `CarHomeScreen.HomeSkeleton` → `CarRowSkeleton(spacing = SkeletonSpacing, modifier = …)`;
      delete `SkeletonRowHeight` and `SkeletonRowCount`.
- [x] `CarFavouriteMusicScreen.FavouritesSkeleton` → same with `SectionSpacing`; delete
      `SkeletonRowCount`.
- [x] `CarSearchResultsScreen.ResultsSkeleton` → same with `RowSpacing`, no padding; delete
      `SkeletonRowCount`.
- [x] `CarDetailScreen.DetailSkeleton` keeps its `Column` and hero `Box`, and uses the shared one
      for the row list with `HeroSpacing`; delete `SkeletonRowHeight` and `SkeletonRowCount`.
      **Add no top padding or spacer to the nested call.** The hero and the rows are children of
      one `Column(spacedBy(HeroSpacing))` today; a nested `spacedBy` adds no leading space, so the
      24dp hero-to-first-row gap survives on its own. Any padding "to be safe" makes it 48dp.
- [x] Prune imports per file, by checking each symbol rather than assuming: `Box` and `height` go
      stale in Home, Favourites and Results; `CarListRowHeight` goes stale in Favourites and
      Results. `CarRaised` and `RoundedCornerShape` **stay** — Favourites' hero, Detail's hero and
      Results' top-result card all still use them.

**Acceptance criteria:** `grep -rn "SkeletonRowHeight\|SkeletonRowCount" automotive/src/main`
returns nothing, and each screen's wrapper is a single call.

**Went one step further than planned.** `HomeSkeleton`, `FavouritesSkeleton` and `ResultsSkeleton`
were deleted outright rather than reduced to one-line wrappers: each was called from exactly one
`when` branch, and those branches already carry multi-line composable calls, so the wrapper was an
indirection with nothing left to hide. `DetailSkeleton` stays — it owns the hero. Net −74 lines
across the four screens.

## Task 3: Gate

- [ ] Run the broader gate.
- [ ] Confirm no new `detekt-baseline.xml` entries.

**Acceptance criteria:** green without touching the baseline.

## Task 4: See it

A pure refactor of drawing code is not proven by a compiler.

- [ ] Install on `AAOS_AOSP_33_userdebug` and cold-start the app: Home shows its skeleton for
      20–30s while the feed loads, which is long enough to screenshot.
- [ ] Open an album to catch `CarDetailScreen`'s skeleton, and confirm the hero still sits 24dp
      above the first row.
- [ ] Compare against `git stash`-ed screenshots of the same two screens, or against the ones in
      this session's scratchpad if they still show the pre-change skeleton.

**Acceptance criteria:** Home and Detail look the same as before the change.

## Task 5: Docs

- [ ] Record the component in `docs/aaos-DESIGN.md`'s component table, replacing the
      `CarLoadingSkeleton` row that was never built with the name that now exists.
- [ ] Add the decision that its height is fixed and its spacing is not.
- [ ] Ticket status and outcome.

**Acceptance criteria:** the design doc names a component that exists.
