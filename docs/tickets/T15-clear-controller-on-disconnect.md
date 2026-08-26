# T15 - The collector's controller field outlives the connection it names

- **Slice:** cleanup, correctness of local state
- **Depends on:** may be absorbed by T14 — see Notes
- **Status:** Filed, not specced
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest`
- **Design Reference:** `docs/aaos-DESIGN.md` D63
- **Risk Tags:** lifecycle, listener ownership
- **Affected Modules:** `:core:playback`

## Problem

`BasePlayerStateCollector` assigns `controller = mc` when the future resolves and never clears it.
`releaseController()` only calls `MediaController.releaseFuture(...)`. There is no
`MediaController.Listener` anywhere in the repo — the collector's listener is a `Player.Listener`,
which carries no disconnect signal — so nothing observes the connection ending.

T11 made this survivable by asking `isConnected` at command time, so the field being stale is no
longer a correctness bug. It is still a lie: a field named `controller` that holds an object which
cannot be used.

## Scope

- Attach a `MediaController.Listener` and clear the field in `onDisconnected`.
- Decide where it is attached: `MediaController.Builder` in `PlaybackModule` builds the future, but
  the collector is what holds the field, and the future is shared.
- Keep T11's command-time check regardless: the callback is asynchronous, so there is a window where
  the session is gone and the field is not yet null.
- Make sure no error is raised by legitimate teardown — `onCleared()`, `releaseController()`, process
  death.

## Out Of Scope

- Reconnection (T14).
- Changing the availability predicate, which stays as the source of truth.

## Acceptance Criteria

- Given a controller disconnects, then the collector's field becomes null without any user action.
- Given normal teardown, then no playback error is raised.
- Given the field is stale for the window before the callback, then commands still refuse correctly.

## Notes

Small on its own, but it needs the same answer T14 needs — who owns the future and its listener — so
if T14 is taken first this may disappear into it. Filed separately because it is worth doing even if
T14 is deferred indefinitely.
