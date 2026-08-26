# T14 - A dead controller stays dead for the life of the process

- **Slice:** playback lifecycle
- **Depends on:** T11 (merged, PR #47) reports the condition. T17 would make it testable.
- **Status:** Filed, not specced
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest :app:assembleDebug`
- **Design Reference:** `docs/aaos-DESIGN.md` D63; `core/playback/.../di/PlaybackModule.kt`
- **Risk Tags:** lifecycle, both surfaces, singleton state, hard to test
- **Affected Modules:** `:core:playback`, `:app`, `:automotive`

## Problem

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
