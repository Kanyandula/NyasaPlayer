# T16 - The controls look alive when there is nothing behind them

- **Slice:** UX, both surfaces
- **Depends on:** T11 (merged, PR #47)
- **Status:** Closed — superseded by T14, see Outcome
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

## Outcome

**Closed without building it, because nothing known can produce the state it would show.**

The ticket assumed a player that could be "gone" while the UI still drew it. Since T14 there is no
known way to get there while the process lives:

- `PlaybackService` shares the app process, and a held `MediaController` keeps it bound. T11's device
  pass on the AAOS emulator found `am stopservice` returns "Service stopped" while the session stays
  bound and alive, and `am force-stop` takes the Activity with the service. The same holds for the
  service's own `onTaskRemoved` → `stopSelf()`: a bound service is not destroyed by it.
- The one known way to a dead controller in a live process was the app releasing its own on
  `onCleared()`. T14 fixed that (D65).
- The windows that remain do not need dimming. Before the first connection the snapshot has no song,
  so there is no player UI to dim; a connection that fails already raises `onControllerConnectionFailed`;
  and a failed rebuild can only follow a disconnect no one has reproduced.

**The tripwire.** `BasePlayerStateCollector.onControllerLost()` now logs once per rebuild attempt, saying
whether the controller it found was `null` or `disconnected`, and logs the cause if the rebuild
fails. `null` is expected — a tap before the first connection resolved. **A `disconnected` line on a
device reopens this ticket**, with the evidence T11 could never collect.

It no longer goes to logcat only. T27 added `onControllerFoundDisconnected()`, which both surfaces
override to record a Crashlytics non-fatal carrying the fixed message "T16 tripwire: transport
command found a disconnected controller" and the `surface` key — so the line reaches us from a
device nobody has plugged in. A report arriving is what reopens this ticket.

**For whoever reopens it:** the push in Notes would be a `MediaController.Listener` on the `Builder` in
`ControllerConnection`. T15 rejected that listener for clearing a field; publishing state is a fair use.
