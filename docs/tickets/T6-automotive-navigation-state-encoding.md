# T6 - Car navigation state is N booleans where the gate wants one value

- **Slice:** structural follow-up after A6, wanted before A7
- **Depends on:** A6 search sheet
- **Status:** Planned with T5; ready to implement as a stacked PR sequence
- **Plan:** `docs/superpowers/plans/2026-08-21-aaos-t5-t6-navigation-content-cap.md`
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest`
- **Design Reference:** `docs/aaos-DESIGN.md` D36-D40 · `automotive/.../navigation/CarUiLocation.kt`
- **Risk Tags:** navigation correctness · driver distraction compliance · A7 copy-paste surface
- **Affected Modules:** `:automotive`

## Problem

`AuthenticatedApp` holds `showFullPlayer`, `showQueue` and `showSearch` as separate booleans, and
`carUiLocation()` collapses them into `CarUiLocation`'s single `overlay` and `sheet` fields with a
priority `when`. That encoding is lossy in both directions:

- `gate()` returns `Denied(reason, evictTo: CarUiLocation)` — a complete destination — but the
  caller reads only `evictTo.tab` and hand-reconstructs the rest by nulling each variable in turn.
- `CarFullPlayerScreen`'s queue button sets `showQueue = true` while `showFullPlayer` is still
  true, so the queue renders above the full player while `location.overlay` reports `FullPlayer`.
  Both are `Allowed` today, so nothing misbehaves — but the gate is meant to be authoritative
  about where the user is, and here it is not.

A6 made the cost concrete: every place that navigates away now has to call `closeSearch()` —
tab select, expand player, open queue, play a result, and the denial branch. A7's Settings and
Profile sheets each add another variable and another call at every one of those sites, where a
missed call is a sheet left open behind the new screen.

## Scope

- Replace `showFullPlayer`/`showQueue` with `var overlay: CarOverlay?` and `showSearch` with
  `var sheet: CarSheet?`.
- Collapse the per-variable teardown into clearing those two values.
- Document on `CarUiLocation.tabRoot()` that `CarUiLocation` is a lossy projection — `drillDepth:
  Int` cannot rebuild a `CarDestination` — so `location = result.evictTo` is not available even
  though `evictTo`'s type suggests it.
- Keep `SearchSheet`-style per-sheet composables. Two sheets do not justify a sheet host, and A7
  should not build one.
- Settle whether a full-screen sheet needs a click-consuming root. `CarSearchScreen` and
  `CarQueueScreen` both draw over `BrowseShell` without consuming pointer input, so a touch on an
  empty area of either may reach the rail or mini-player underneath. `closeSearch()` is called
  from `onExpandPlayer` and `onQueueClick` partly as insurance against that; if the sheets consume
  their own touches, those calls are dead and can go with the rest of the teardown.

## Out Of Scope

- Changing what `gate()` decides.
- Introducing a navigation library; this stays `rememberSaveable` state in one composable.

## Acceptance Criteria

- Given any overlay or sheet is open, when the gate denies the location, then it closes without a
  per-variable teardown list.
- Given the queue is opened from the full player, then `CarUiLocation.overlay` names what is
  actually on top.
- Given A7 adds a sheet, then it adds an enum case and one render branch, not a call at every
  navigate-away site.

## Notes

Found by an altitude review of A6. The A6 slice itself is small enough to live with the current
encoding; the point of doing this before A7 is that A7 doubles the number of sheets.
