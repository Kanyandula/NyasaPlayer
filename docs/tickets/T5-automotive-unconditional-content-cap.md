# T5 - BrowseShell caps parked content to the driving limit

- **Slice:** cleanup follow-up after A6
- **Depends on:** `UxRestrictionState.cap()` (A6)
- **Status:** Ready to spec
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest`
- **Design Reference:** `docs/aaos-DESIGN.md` D36
- **Risk Tags:** driver distraction compliance · content truncation · A3/A4 regression surface
- **Affected Modules:** `:automotive`

## Problem

`BrowseShell` in `AutomotiveApp.kt` applies `.take(maxItems)` unconditionally at ten call sites —
Home's recently-played and popular rows, Browse's genres, Library's playlists, albums, artists and
recently-played, the artist drill-down, and Favourites.

`maxCumulativeContentItems` is reported by the platform when parked as well, where it is the
unrestricted baseline and not a restriction. So a parked driver's lists are trimmed to a driving
cap for no reason. A5's `queueDisplayItems()` and A6's search results both gate the cap on
`isDistractionOptimized`; these ten call sites are the remaining inconsistency, and they are the
ones on the wrong side of D36.

## Scope

- Replace the ten unconditional `.take(maxItems)` calls with `restrictions.cap(...)`.
- Decide per screen whether an unbounded parked list needs a lazy-list check — Favourites and
  Library can be large, and these caps have been masking that since A3.
- Add coverage that a parked screen is not truncated, for at least one list per screen.

## Out Of Scope

- Reopening D36 itself.
- Changing `queueDisplayItems()`, which already windows correctly.
- Search results, which A6 already moved onto the shared cap.

## Acceptance Criteria

- Given the vehicle is parked, when a screen renders a list longer than
  `maxCumulativeContentItems`, then every item is reachable.
- Given the vehicle is moving, when the same screen renders, then the list is capped exactly as it
  is today.
- Given the caps are removed while parked, then no screen regresses in scroll performance on the
  AAOS emulator with a full library.

## Notes

Found by an altitude review of A6, which also established that D36's original wording wrongly
claimed the unconditional form was the house rule.
