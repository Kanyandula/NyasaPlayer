# T7 - Four copies of the same list skeleton

- **Slice:** cleanup follow-up after A6
- **Depends on:** —
- **Status:** Implemented; the loading states could not be captured on the emulator — see Outcome
- **Verification Command:** `./gradlew :automotive:assembleOemDebug detekt`
- **Plan:** `docs/superpowers/plans/2026-08-25-aaos-t7-shared-list-skeleton.md`
- **Design Reference:** `docs/aaos-DESIGN.md` component table, `CarLoadingSkeleton` row
- **Risk Tags:** duplication · loading-state consistency
- **Affected Modules:** `:automotive`

## Problem

The same static loading skeleton — `repeat(n) { Box(fillMaxWidth, height, clip, background(CarRaised)) }`
— now exists four times: `CarHomeScreen`, `CarFavouriteMusicScreen`, `CarDetailScreen`'s tail, and
A6's `CarSearchResultsScreen`. The design's component table already lists `CarLoadingSkeleton` as
a shared component; it was never built, so each screen wrote its own.

They have already drifted on dimensions — A6's copy initially re-declared an 80dp row height that
`AutomotiveDimens.CarListRowHeight` already provides and `CarFavouriteMusicScreen` already uses.
A6 fixed its own copy to use the token; the other three were left alone as out of scope.

## Scope

- Add `CarRowSkeleton(rows: Int = 4, spacing: Dp, modifier: Modifier)` to `ui/components/`.
- Collapse all four call sites onto it. `CarDetailScreen` keeps its hero Box and uses the shared
  one only for its row list.
- Keep the "static placeholders, no shimmer" rule and its reason (decorative motion is gated on
  vehicle state) as the component's doc comment, so it is stated once instead of four times.

## Out Of Scope

- Introducing shimmer or any animation.
- Changing when each screen decides to show a skeleton.

## Acceptance Criteria

- Given any of the four screens is loading with no content, then it renders the shared skeleton and
  no screen declares its own row-height constant.
- Given detekt runs, then no new baseline entries appear.

## Notes

Found by a reuse/simplification review of A6, which counted the copies.

## Outcome

`CarRowSkeleton` lives in `ui/components/` and the four copies are gone. Design record D60, and the
component table row that named a never-built `CarLoadingSkeleton` now names what exists.

The ticket counted four copies; there are six skeletons in `:automotive`. The other two —
`LibrarySkeleton` and `BrowseSkeleton` — draw card grids rather than row lists and were left alone
on purpose (D60).

Three of the four screens lost their wrapper composable entirely rather than keeping a one-line
one: each was called from a single `when` branch that already carries multi-line composable calls,
so the wrapper had nothing left to hide. `CarDetailScreen` keeps its wrapper because it owns the
hero. Net −74 lines across the four screens, +48 for the component.

Two things the spec had wrong, both caught by external review before implementation: a bare
`repeat(4)` would have tripped `MagicNumber` — which is exactly why each screen had declared the
same constant — and the no-shimmer rationale the plan proposed to write was already in
`CarHomeScreen`, so it was moved rather than invented.

Gates: 171 tests, detekt clean with the baseline untouched, lint 0 errors, both flavors assembling.

## Not verified

**The rendered result.** Four attempts on `AAOS_AOSP_33_userdebug` could not hold a loading state
on screen: Home, Favourites and playlist detail all render instantly from Room and Firestore's
offline cache, so `isLoading && content.isEmpty()` never lasts long enough to screenshot, and
throttling the network does not help because nothing is being fetched.

The equivalence argument is arithmetic, not visual: identical `Box` modifiers, the same 80dp at all
four call sites, each spacing value passed through unchanged, and a nested `spacedBy` that adds no
leading space so `CarDetailScreen`'s 24dp hero gap is untouched.

Closing it means making the loading state real rather than waiting for it — sign out and back in,
or clear app data and sign in — which needs an interactive Google account tap. Worth folding into
the next device run that signs in anyway.
