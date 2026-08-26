# T11 - A dead controller leaves a fully drawn player whose buttons do nothing

- **Slice:** playback robustness - both surfaces
- **Depends on:** —
- **Status:** Filed, not specced
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest :app:assembleDebug`
- **Design Reference:** `docs/aaos-DESIGN.md` D61; `core/playback/.../BasePlayerStateCollector.kt`
- **Risk Tags:** silent failure, both surfaces, user-visible
- **Affected Modules:** `:app`, `:automotive`, possibly `:core:playback`

## Problem

Observed on `Medium_Phone_API_35`, 2026-08-26, and reported by the user before it was diagnosed:
music appeared to be playing, no sound came out, and skip did nothing.

The app process had been force-stopped underneath a live UI (`dumpsys activity exit-info`:
`reason=10 (USER REQUESTED) subreason=21 (FORCE STOP)`), and at that point the app had **no
`ServiceRecord` and no `MediaSession`** — while the player was still fully drawn on screen.

Every transport action on both surfaces opens with the same line:

```kotlin
val controller = stateCollector.controller ?: return
```

Seven of them in `PlayerViewModel`, ten in `AutomotivePlayerViewModel`. With a dead or
never-connected controller each one returns silently, so:

- play/pause, skip, seek, repeat and shuffle do nothing at all;
- `playSong` still updates `_uiState` optimistically first, so the UI flips to "playing" and the
  track changes on screen while nothing reaches the player;
- no error surfaces, because `onControllerConnectionFailed` covers *failed to connect*, not
  *connected and then died*.

The result is the worst kind of failure for a driver or a listener: the app looks like it is
working. On the car surface this is worse than on mobile, because the driver cannot pull over to
investigate and the affordances all still look live.

## Scope

- Decide what a transport action should do when the controller is gone, once, for both surfaces.
- Surface it: the existing `PlayerError` channel already carries "Could not connect to playback
  service" and routes to a snackbar on mobile and `CarErrorOverlay` on the car.
- Consider whether the collector should attempt a reconnect rather than only reporting — the
  future is created once in `PlaybackModule`, so a dead controller is currently terminal for the
  ViewModel's lifetime.
- Keep the optimistic UI honest: `playSong` should not paint a playing state it could not send.

## Out Of Scope

- Restore behaviour (T3, T10 — a null controller there already returns null and shows nothing).
- Making `MediaController` unit-testable (the transport seam, still unfiled).

## Acceptance Criteria

- Given the controller is gone, when any transport action is invoked, then the user sees the
  playback-unavailable error rather than nothing.
- Given the controller is gone, when a song is tapped, then the UI does not show it as playing.
- Given the controller is alive, then nothing about today's behaviour changes.

## Notes

Found while verifying T10 on the phone AVD, and initially mistaken for contradictory evidence about
restore — `docs/T10_VERIFICATION.md` records that confusion, which this ticket explains: the
session had existed and gone away, leaving exactly this state.

Not caused by T10. The guards predate it on both surfaces; T10 only moved restore policy.

Seventeen call sites share the pattern, so the fix belongs behind one helper rather than in each
method — `BasePlayerStateCollector` already owns the controller and would be the natural home,
which also keeps the two surfaces from answering differently, as they did for restore before T10.