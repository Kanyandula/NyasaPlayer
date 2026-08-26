# T13 - Transport actions ask the controller directly, thirteen times

- **Slice:** architecture - the same move T10 made for restore, applied to transport
- **Depends on:** T10 (merged, PR #45). Unblocks T11.
- **Status:** Implemented; car device-verified nine of ten, mobile not run — see Outcome
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest :app:assembleDebug`
- **Design Reference:** `docs/aaos-DESIGN.md` D61, and D62 for the outcome
- **Plan:** `docs/superpowers/plans/2026-08-26-aaos-t12-t13-playback-cleanups.md`
- **Verification:** `docs/T13_VERIFICATION.md`
- **Risk Tags:** both surfaces, every transport path, no device coverage for mobile
- **Affected Modules:** `:core:playback`, `:app`, `:automotive`

## Problem

Every transport action on both surfaces starts the same way:

```kotlin
val controller = stateCollector.controller ?: return
```

Five times in `PlayerViewModel` and eight in `AutomotivePlayerViewModel` once T12 has taken the
four command senders out of the count. Each then talks to `MediaController` directly — `play`,
`pause`, `seekTo`, `seekToNextMediaItem`, `repeatMode`, `removeMediaItems`, and so on.

Three consequences:

1. **T11's defect has thirteen homes.** When the controller is gone, every one returns silently and
   the UI keeps showing a working player. Fixing that where it is means thirteen edits and two
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

A `PlayerTransport` class in `:core:playback`, over a `() -> MediaController?` supplier and exposed
by `BasePlayerStateCollector` as a property. One `controller ?: return` per operation, in one place;
each ViewModel calls `stateCollector.transport.…` and keeps only its own reaction.

Not more methods on the collector itself: it has ten already against detekt's 16-function class
threshold, and this project does not answer a threshold with a suppression (D23). Its own class is
the better shape regardless — a transport is constructible in a test from a supplier that returns
null, with no collector involved.

**Explicitly not proposed: an interface over `MediaController`.** The union of members the two
surfaces touch is about seventeen, so a seam means an interface, a delegating implementation and a
fake — a large surface invented for one consumer, and every transport path rewritten to go through
it. What that buys is the ability to assert *what was sent to the controller*. What T11 needs, and
what the recurring defect actually is, is the **null-controller branch** — and that is testable
the moment the branch lives behind one class, and that class needs nothing but a supplier to
construct.

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

- Given a transport with no controller, when any operation runs, then it returns `false` rather
  than silently doing nothing, and a unit test asserts that per operation.
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

## Outcome

`PlayerTransport` holds the thirteen operations both ViewModels used to open with
`controller ?: return`. 102 lines left the ViewModels, 108 arrived in one class, and neither
ViewModel reaches `stateCollector.controller` for transport any more — the only two reaches left on
each side are T12's queue senders.

The class exists instead of more collector methods because of detekt's 16-function class threshold
(D62), and that constraint produced the better shape: `PlayerTransport { null }` is the entire test
harness. `PlayerTransportTest` is 14 cases covering every operation's no-controller branch plus a
roll-call that catches an operation added later without its own test — **the first automated coverage
any transport path in this project has had.** Mutations: `skipNext` returning `true` unconditionally
fails its named test and the roll-call; `isPlaying()` falling back to `false` fails
`isPlaying_noController_isUnknownRatherThanFalse`.

Two things surfaced while doing it. `togglePlayPause` needed `isPlaying(): Boolean?` on the transport,
because mobile decides from the live player and the snapshot lags a listener callback behind — using
the snapshot would have been a quiet behaviour change. And `toggleShuffle` now writes its optimistic
flag only when the command was actually sent, on both surfaces; previously each wrote the flag first
and hit `controller ?: return` after, which came to the same thing by accident of ordering.

## Not verified

Nine of ten car operations are recorded field by field in `docs/T13_VERIFICATION.md`.
`skipNext`'s repeat-all wrap was not observed — the run stopped one tap short of the state that
exercises it — and the driving-state refusal was not run, though it tests a `CarQueueScreen`
contract this ticket does not touch.

**Mobile was not run at all.** Its transport set, plus `dismiss()` and the offline-buffering pause,
still needs a session with a signed-in phone — the same session that owes T3's D55 fix and T10's
restore pass.
