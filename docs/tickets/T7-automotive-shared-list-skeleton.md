# T7 - Four copies of the same list skeleton

- **Slice:** cleanup follow-up after A6
- **Depends on:** —
- **Status:** Ready to spec
- **Verification Command:** `./gradlew :automotive:assembleOemDebug detekt`
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
