# T14 - A dead controller stays dead for the life of the process

- **Slice:** playback lifecycle
- **Depends on:** T11 (merged, PR #47) reports the condition. T17 would make it testable.
- **Status:** Implemented; the failure mode is proved in tests, not on a device — see Outcome
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest :app:assembleDebug`
- **Design Reference:** `docs/aaos-DESIGN.md` D63, and D65 for the outcome
- **Plan:** `docs/superpowers/plans/2026-08-26-aaos-t14-reconnection.md`
- **Verification:** `docs/T14_VERIFICATION.md`
- **Risk Tags:** lifecycle, both surfaces, singleton state, hard to test
- **Affected Modules:** `:core:playback`, `:app`, `:automotive`

## Problem

**Update, 2026-08-26 — proved before implementation.** The ticket below describes a controller dying
and no way back. The sharper problem is that **the app kills its own controller in the ordinary
course of events**: `releaseController()` releases a `@Singleton` future, both ViewModels call it from
`onCleared()`, and `SharedControllerFutureTest` shows the future then hands the same, disconnected
instance to the next consumer. Back out of the app with the process alive, return, press play —
nothing happens, and T11 reports a failure the app inflicted on itself.

That is very likely the mechanism behind the 2026-08-26 bug report, which T11 left open between a
null controller and a stale one. This is a third answer and the only one requiring nothing to crash.


`PlaybackModule.provideMediaControllerFuture` builds **one** `@Singleton`
`ListenableFuture<MediaController>` for the whole process. `BasePlayerStateCollector.connectController()`
attaches a listener to that future and nothing ever asks for another one.

So when a controller disconnects, it is gone until the process restarts. T11 made that state visible
— every command reports "could not connect to playback service" — but visible is all it is: there is
no path back to a working player short of the user killing and reopening the app.

On the car this is worse than on mobile. A driver cannot restart an app mid-drive, and the media
source they are listening to is the one that stopped answering.

## Scope

- Replace the single shared future with something that can be asked again: a factory, a provider, or
  a small connection-owner that rebuilds on demand.
- Decide who triggers a reconnect — the collector on first failed command, the ViewModel, or a retry
  affordance the user presses.
- Re-register the `Player.Listener`, restart the position poller, and decide what the snapshot shows
  while a reconnect is in flight.
- Decide what happens to two collectors (mobile and car both exist in the same process on a device
  that runs both) sharing one connection.

## Out Of Scope

- Changing what T11 reports, beyond suppressing it while a reconnect is actually in progress.
- Restore behaviour, which already handles a null controller by doing nothing.

## Acceptance Criteria

- Given a disconnected controller, when a reconnect is attempted and succeeds, then playback controls
  work again without restarting the app.
- Given a reconnect fails, then the user is told once and the app does not loop.
- Given a reconnect is in flight, then the UI does not claim playback is available.

## Notes

The honest fix for the condition T11 only reports, and the largest of the four follow-ups. It also
changes `PlaybackModule`'s contract, which every surface depends on — worth a design round before
implementation rather than after.

## Outcome

Implemented across five steps. Design record D65.

The ticket was filed as "a disconnected controller stays dead for the life of the process". Step 1
proved something sharper first: **the app released its own controller in the ordinary course of
events.** `releaseController()` released the process-wide `@Singleton` future from either ViewModel's
`onCleared()`, the future is idempotent, and release is terminal — so the next ViewModel got the same
instance with `isConnected == false`. Backing out and returning was the whole trigger.

`ControllerConnection` now owns it: `acquire()` / `release()` reference-counted, `reconnect()` to
replace what cannot be revived. `PlaybackModule` provides no future at all, and
`ListenableFuture<MediaController>` appears in exactly one production file. A command that finds no
usable controller triggers one rebuild behind an `AtomicBoolean`, and T11's message fires only if
that fails.

Nineteen new tests, all on the JVM, all of them impossible before T17 landed the same evening:
`SharedControllerFutureTest` (the bug), `ControllerConnectionTest` (the ownership rules, including
the unbalanced-release case), `ReconnectingCollectorTest` (rebuild once, report only on failure,
three taps produce one attempt).

Gates: 315 tests, detekt clean with the baseline untouched and no `@Suppress` added, lint clean, both
automotive flavors and `:app:assembleDebug`.

## Not verified

**The failure mode on a device.** Backing out of `AutomotiveActivity` does not finish it on this AAOS
build, so `onCleared()` never runs and the trigger is unreachable there — established by running the
**pre-fix `main` build** through the same gesture and watching it keep playing, not by assumption.
The car pass is therefore a regression check: restore, play, pause, next and play-after-return all
behave, in one process.

Mobile is where finishing the root activity is routine, and that pass is still owed. So is a
driving-state check on the car, though T14 adds no UI.
