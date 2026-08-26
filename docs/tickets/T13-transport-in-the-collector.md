# T13 - Transport actions ask the controller directly, seventeen times

- **Slice:** architecture - the same move T10 made for restore, applied to transport
- **Depends on:** T10 (merged, PR #45). Unblocks T11.
- **Status:** Proposed
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest :app:assembleDebug`
- **Design Reference:** `docs/aaos-DESIGN.md` D61
- **Risk Tags:** both surfaces, every transport path, no device coverage for mobile
- **Affected Modules:** `:core:playback`, `:app`, `:automotive`

## Problem

Every transport action on both surfaces starts the same way:

```kotlin
val controller = stateCollector.controller ?: return
```

Seven times in `PlayerViewModel`, ten in `AutomotivePlayerViewModel`. Each then talks to
`MediaController` directly — `play`, `pause`, `seekTo`, `seekToNextMediaItem`, `repeatMode`,
`removeMediaItems`, and so on.

Three consequences:

1. **T11's defect has seventeen homes.** When the controller is gone, every one returns silently
   and the UI keeps showing a working player. Fixing that where it is means seventeen edits and two
   surfaces that can drift apart again — the situation T10 just removed for restore.
2. **None of it is testable.** `MediaController` is `@DoNotMock` with a package-private
   constructor, so no unit test can reach a ViewModel's transport path. Every claim about skip,
   seek or repeat has to be made on a device.
3. **The two surfaces already disagree in small ways** — mobile runs an offline and download-manager
   pre-flight before `play()`, writes an optimistic position on seek, and keeps `isShuffled` in a
   different place than the car — and nothing structural keeps those decisions together. (`skipNext`
   is *not* one of them: both wrap on repeat-all identically. An earlier draft of this ticket said
   otherwise and was wrong.)

## Proposed solution

Move transport onto `BasePlayerStateCollector`, which already owns the controller and, since T10,
already owns restore. One `controller ?: return` per operation, in one place; each ViewModel calls
a collector method and keeps only its own reaction.

**Explicitly not proposed: an interface over `MediaController`.** The union of members the two
surfaces touch is about seventeen, so a seam means an interface, a delegating implementation and a
fake — a large surface invented for one consumer, and every transport path rewritten to go through
it. What that buys is the ability to assert *what was sent to the controller*. What T11 needs, and
what the recurring defect actually is, is the **null-controller branch** — and that is testable
the moment the branch lives on the collector, because T10 proved the collector is constructible in
a unit test with a future that never completes.

If a later ticket genuinely needs to assert transport calls against a fake player, Media3 ships
`SimpleBasePlayer` and a real `MediaSession` can be stood up in a Robolectric test. That is the
moment to build a seam, with a second consumer to justify it.

## Scope

- Add a `PlayerTransport` class in `:core:playback` over a `() -> MediaController?` supplier,
  exposed by `BasePlayerStateCollector` as a property — not more methods on the collector, which
  would cross detekt's 16-function class threshold and this project does not answer that with a
  suppression.
- Operations: `togglePlayPause`, `skipNext`, `skipPrevious`, `seekTo`, `toggleRepeatMode`,
  `toggleShuffle`, `skipToQueueItem`, `removeFromQueue`, `clearQueue`, `stopAndClear`.
- Each returns `Boolean` meaning "reached the controller" — **not** "had an effect". The existing
  index, current-item and queue-size guards move unchanged and stay silent; they are correct
  refusals, not failures.
- Both ViewModels call them, keeping their own reactions. The differences that are real — mobile's
  offline pre-flight on play, its optimistic seek write, where each surface keeps `isShuffled`, and
  its mobile-only `dismiss()` and offline-buffering pause — are decided per operation with the
  reason recorded, not merged.
- Unit-test the no-controller branch for each operation.

## Out Of Scope

- Surfacing the error in the UI — that is T11, which this unblocks.
- Changing playback behaviour when the controller is alive.
- The command senders (T12).

## Acceptance Criteria

- Given a collector with no controller, when any transport operation runs, then it reports failure
  rather than returning silently, and a unit test asserts that per operation.
- Given `grep -rn "stateCollector.controller" app/src/main automotive/src/main`, then no transport
  method reaches through it.
- Given a device pass on both surfaces, then play, pause, skip, seek, repeat, shuffle and the
  queue actions behave exactly as before.

## Notes

This is the third time the same shape has come up: T3 found restore written twice, T10 moved it to
the collector, and transport is the same duplication one layer over. Doing it after T12 keeps the
import churn in the two ViewModels to one round.

Mobile's transport has no automated coverage today and this ticket does not add device coverage —
it makes the failure branch testable, not the success path. The success path stays device-verified,
and mobile's device debt (T3's D55 fix, T10's restore pass) is still outstanding.