# T16 - The controls look alive when there is nothing behind them

- **Slice:** UX, both surfaces
- **Depends on:** T11 (merged, PR #47)
- **Status:** Filed, not specced
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest :app:assembleDebug`
- **Design Reference:** `docs/aaos-DESIGN.md` D63
- **Risk Tags:** UX, both surfaces, driving-time affordances
- **Affected Modules:** `:core:playback`, `:app`, `:automotive`

## Problem

T11 answers a tap on a dead player with a message. That is better than silence, and it is still an
error after the fact: the driver has already reached for a control that was never going to work.

`PlaybackSnapshot` could carry availability, and both surfaces could dim their transport controls
while it is false — the answer arriving before the tap rather than after it.

## Scope

- Carry availability in `PlaybackSnapshot`, updated from the same predicate T11 added.
- Decide what "unavailable" looks like on each surface: dimmed controls, a hidden mini player, or a
  banner. The car already dims controls for driving restrictions, so the vocabulary exists there.
- Decide whether the T11 error still fires when the controls are visibly disabled, or whether the
  dimming replaces it.

## Out Of Scope

- Reconnection (T14) — though if T14 lands first, "reconnecting" becomes a third state worth showing.

## Acceptance Criteria

- Given an unavailable player, then transport controls are visibly not actionable on both surfaces.
- Given availability returns, then controls return without a restart.
- Given the car is driving, then this must not add a new visual state that competes with the
  distraction-optimized gating.

## Notes

Needs a real answer to "what does the snapshot know and when" — availability is currently asked at
command time, and making it observable means deciding how often it is polled or how it is pushed.
That is the interesting part of this ticket, not the dimming.
