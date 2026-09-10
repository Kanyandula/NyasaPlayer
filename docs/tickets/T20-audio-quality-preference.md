# T20 - An audio-quality preference, and something that reads it

- **Slice:** A7 deferral
- **Depends on:** `PlaybackService` learning to vary quality
- **Status:** Filed, not specced
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest detekt`
- **Design Reference:** `docs/aaos-DESIGN.md` D70; `docs/AAOS_SCREEN_CONTRACT.md` screen 14
- **Risk Tags:** FR-2.6 (controls that do nothing), streaming cost
- **Affected Modules:** `:core:playback`, `:core:data`, `:automotive`, `:app`

## Problem

The contract lists an audio-quality row on screen 14. Nothing in `PlaybackService` reads a quality
setting, so the row would persist a value no one observes — a control that lies, in the same way
FR-2.6's disabled-looking-live controls do. A7 shipped Settings without it.

## Scope

- Decide what the levels mean against what the catalogue actually stores. A `Song` carries one
  URL today; if there is no second bitrate to choose, the preference has nothing to select
  between and this ticket is really a backend one.
- If there is: where the preference is stored, whether it is per-user or per-device, and what
  happens to a stream already playing when it changes.
- Only then add the row, on both surfaces — the mobile app has the same gap.

## Out Of Scope

- A cellular-data toggle, a cache-clear row, or a theme row. Same rule: nothing reads them.

## Acceptance Criteria

- Given a driver changes the quality preference while parked, then the next stream started uses it.
- Given no reader exists, then no row ships.

## Notes

First question to answer is whether the catalogue has more than one rendition per song. If it does
not, close this and reopen it against the backend.
