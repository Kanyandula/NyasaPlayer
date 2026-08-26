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

The app had **no `ServiceRecord` and no `MediaSession`** — `dumpsys` listed only telecom and
Bluetooth — while the player was fully drawn on screen, with a track title, a progress bar and live
controls.

### Why every button was silent

Two facts combine.

**The controller field is never cleared.** `BasePlayerStateCollector` assigns `controller = mc` once,
when the future resolves, and nothing ever sets it back to null — `releaseController()` only releases
the future. So when the service dies, the field keeps a `MediaController` whose session is gone.

**Everything guards on null, not on usable.** `PlayerTransport.withController` returns early only when
the supplier returns `null`. With a stale-but-non-null controller it proceeds, calls into a dead
session, and returns `true` — reporting success. `playSong` and `shufflePlay` do not go through the
transport at all: they call `stateCollector.controller?.sendSetQueue(...)` and then write an
optimistic playing state, so the UI paints a track as playing that never reached a player.

The result is the worst failure shape available: **the app looks like it is working.** On the car
that is a driver tapping live-looking affordances on the move; on mobile it reads as a broken app
rather than a broken connection.

This was mistaken, during T10's verification, for contradictory evidence about restore — a restored
mini player on screen with no session behind it. `docs/T10_VERIFICATION.md` records that confusion;
this ticket is its explanation.

## Scope

**Make "usable" the question.** One predicate inside `PlayerTransport` — the controller is available
when it is non-null *and* `isConnected` — used by every operation and by `isPlaying()`. Without the
second half this ticket does not fix the reported bug.

**Report through one path.** A fifth `protected open fun onPlayerUnavailable()` hook on
`BasePlayerStateCollector`, called by the transport when that predicate fails, overridden by each
ViewModel to raise the `PlayerError` its surface already renders. Thirteen call sites stay untouched
and a fourteenth operation reports for free.

**Close the paths the hook cannot see.** Move `setQueue` and `shufflePlay` onto the transport so the
play-entry paths report too, and write their optimistic state only on success. Report explicitly at
mobile's `togglePlayPause` early return, which exits on `isPlaying() == null` before reaching any
reporting call.

**Make mobile's message visible.** `isPlaybackError = false` routes to the snackbar, whose host sits
inside the `Scaffold` while `GlobalPlayerLayer` draws after it — so today the message would appear
behind the expanded player, exactly where the dead buttons are. Move the host above the player layer.

**Keep the refusals silent.** The index, current-item and queue-size guards return `true` and must
raise nothing (D62).

## Out Of Scope

- Restore behaviour (T3, T10 — a null controller there already returns null and shows nothing).
- Making `MediaController` unit-testable (the transport seam, still unfiled).

## Acceptance Criteria

- Given the controller is gone, when any transport action is invoked, then the user sees the
  playback-unavailable error rather than nothing.
- Given the controller is gone, when a song is tapped, then the UI does not show it as playing.
- Given the controller is alive, then nothing about today's behaviour changes.

## Why this over the alternatives

- **Check at each call site** — `if (!transport.skipNext()) report()` thirteen times. It works, and
  it is the version that rots: two surfaces free to answer differently, and the fourteenth operation
  silently forgetting. This series has removed that shape twice already (D61, D62); reintroducing it
  for error handling would be perverse.
- **Clear `controller` on disconnect and keep checking null.** Media3's `MediaController.Listener`
  does have `onDisconnected`, so the field *could* be made honest. It is the tidier model — "this
  field means a usable controller" — and worth doing eventually. It is not sufficient on its own:
  the callback is asynchronous, so there is a window where the session is gone and the field is not
  yet null, which is precisely the window a driver taps in. The predicate closes that window without
  waiting for a callback; clearing the field is a follow-up that makes the model cleaner, not a
  substitute.
- **Reconnect instead of reporting.** The honest fix for the underlying condition, and far larger:
  `PlaybackModule` provides a single `@Singleton` future, so recovery means a future factory,
  re-registering the listener, restarting the position poller and deciding what the snapshot shows in
  between. It also does not remove the need for a message — something must be said while recovery is
  attempted or after it fails. Its own ticket.
- **Disable the controls instead of showing an error.** Carry availability in the snapshot and dim
  the transport buttons. Arguably better once it exists, and strictly more work on both surfaces —
  and it answers "why is nothing happening" with a greyed button rather than a reason. Worth
  considering as a follow-up on top of this, not instead of it.
- **This proposal.** It fixes the reported symptom rather than the one the ticket title suggests,
  reuses machinery three tickets have already built — the transport, the hook mechanism, both error
  channels — and adds one predicate, one hook and two moved senders. It is also the first item in
  this series whose device check **reproduces on demand**: force-stop the app under a live UI and
  every button is dead, before and after.

## Notes

Seventeen call sites shared this pattern when the ticket was filed. **T13 gathered them into
`PlayerTransport`**, which is what makes this ticket small: one predicate, one hook, two moved
senders, rather than thirteen edits across two ViewModels.

Found while verifying T10 on the phone AVD. Not caused by T10 or T13 — the guards predate both.

External review (`codex exec`, 2026-08-26) is what turned it from a null check into the ticket above:
the null-only version would not have fixed the bug that produced it.
