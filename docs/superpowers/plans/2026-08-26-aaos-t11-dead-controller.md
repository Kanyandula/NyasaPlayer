# AAOS T11 - Dead Controller Implementation Plan

> **For agentic workers:** T13 already built the machinery. This slice decides what the driver and
> the listener *see* when a transport call finds no player, and wires it once per surface. Do not
> add error handling to the guards that legitimately refuse — see D-T11.2.

**Goal:** A player that cannot reach its service stops looking like a working one. Today every
transport action returns silently and the UI keeps its affordances live; on the car that is a driver
tapping a dead button on the move.

**Ticket:** `docs/tickets/T11-dead-controller-silent-noop.md`

**External review:** `codex exec`, 2026-08-26 — two blockers and five should-fixes, all folded in.
One of them, D-T11.0, changes what this ticket is: as planned it would not have fixed the symptom
that produced it.

**Verification command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest :app:assembleDebug`

**Broader gate:** the usual set, plus a device pass per surface — and this one is reproducible, which
the last two device passes were not: force-stop the app underneath a live UI and every button is dead.

## Current baseline

`main` after PR #46.

- `PlayerTransport` owns every transport action and already returns `false` when there is no
  controller; `isPlaying()` already returns `null`. Fourteen unit tests cover that branch.
- `BasePlayerStateCollector` exposes it as `transport` and already has four `protected open` hooks
  the ViewModels override.
- Both surfaces already have an error channel: `PlayerError` with `isPlaybackError`, routed to a
  snackbar on mobile (`NyasaPlayerApp.kt:47`) and to `CarErrorOverlay` on the car
  (`AutomotiveApp.kt:397`). `onControllerConnectionFailed` already produces "Could not connect to
  playback service" on both.

So nothing new is needed in the transport, the collector's hook mechanism, or either error channel.
What is missing is the connection between them.

## Decisions

### D-T11.0: "Unavailable" means null **or** disconnected — and only the second one matches the bug

`BasePlayerStateCollector` sets `controller = mc` once and never clears it; `releaseController()`
only releases the future. So after the service dies the field still holds a `MediaController`, and
`PlayerTransport.withController` — which reports only when the supplier returns `null` — would sail
straight through and call methods on a corpse.

That is exactly the state the reported symptom came from: a fully drawn player, no `ServiceRecord`,
no `MediaSession`, every button silent. **A ticket that only handles `null` would have shipped
without fixing the thing that was reported.**

So: treat a controller as unavailable when it is `null` *or* `!isConnected`. `MediaController`
exposes `isConnected` for this. One predicate, inside the transport, used by every operation and by
`isPlaying()`.

Whether the collector should also clear `controller` on disconnect is a separate question and not
required here — the transport asking is enough, and clearing the field would need the listener
lifecycle thought through.

### D-T11.1: One hook, not thirteen call sites

Add a fifth hook beside the existing four:

```kotlin
protected open fun onPlayerUnavailable() {}
```

`PlayerTransport` calls it when a controller is missing, and each ViewModel overrides it to raise the
error its surface already knows how to show. Thirteen call sites stay untouched, and an operation
added later reports for free.

The alternative — `if (!transport.skipNext()) reportPlayerGone()` at every call site — is thirteen
edits, two surfaces that can drift, and a fourteenth operation that silently forgets. That is exactly
the shape T10 and T13 removed.

**Wiring:** the transport already takes a supplier; give it a second constructor parameter, an
`onUnavailable: () -> Unit` defaulting to `{}`, and have the collector pass `{ onPlayerUnavailable() }`.
Its fourteen unit tests keep working with the default, and new tests assert the callback fires.

`isPlaying()` stays **side-effect-free** — it is a query, and mobile calls it as one. If it fired the
callback too, mobile's `togglePlayPause` would report twice for one tap: once from the query and once
from its own early return.

### D-T11.2: Only "no controller" reports. Refusals stay silent.

`PlayerTransport`'s `Boolean` means *reached the controller* (D62). The index, current-item and
queue-size guards return `true` and must raise nothing: a driver tapping remove on the track that is
playing has not hit an error, and `clearQueue` on a queue of one is a refusal the UI already shows by
dimming the button.

This is the finding most likely to be got wrong by someone reading only the ticket title.

### D-T11.3: The message is the one both surfaces already use — but mobile cannot currently show it

`onControllerConnectionFailed` produces "Player Error / Could not connect to playback service" today,
with `isPlaybackError = false`. Reuse the wording verbatim; a second phrasing for one condition is how
two surfaces start describing the same failure differently.

**The car is fine.** `AutomotiveApp` renders any `playerState.error` as `CarErrorOverlay` above the
full player and the queue, `isPlaybackError = false` only changes its icon, and Retry stays hidden
unless `isRetryable` — which this is not. The overlay also blocks the controls underneath, so repeated
taps cannot stack.

**Mobile has a layering problem.** `isPlaybackError = false` routes to the snackbar, whose host lives
in the `Scaffold`; `GlobalPlayerLayer` is drawn *after* the `Scaffold` in the same `Box` and the
expanded player fills the screen. So the message a user gets while tapping dead buttons in the
expanded player is behind the expanded player.

Move `AppSnackbarHost` out of the `Scaffold` and into the `Box` after `GlobalPlayerLayer`, keeping
its bottom-bar padding. That fixes every non-playback error, not just this one. If the padding proves
fiddly, the fallback is to let the expanded player's inline banner render non-playback errors too —
but that changes routing semantics for errors beyond this ticket, so prefer the move.

**One report per dead session.** Mobile's `LaunchedEffect` shows the snackbar and then calls
`clearError()`, so each tap would re-raise and re-show it. Raise the unavailable error only when no
error is currently showing.

### D-T11.4: The paths the hook cannot cover, named

Two, and both would otherwise ship silent:

1. **Mobile's `togglePlayPause`** returns early on `isPlaying() == null`, and that query does not
   report (above). Report explicitly at the early return.
2. **Both surfaces' play-entry paths.** `playSong` and `shufflePlay` still call
   `stateCollector.controller?.sendSetQueue(...)` directly and then write an optimistic playing
   state — so with a dead controller they paint a track as playing that never reached the player.
   That is the ticket's second acceptance criterion, and the plan's first draft missed it.

   Fix it the way T13 fixed transport: put `setQueue` and `shufflePlay` on `PlayerTransport`, over
   the same predicate and the same callback, and have both ViewModels write their optimistic state
   only when the call returned `true`. That finishes what T12 started — the senders stay in
   `PlaybackCommands.kt`, the transport just calls them.

### D-T11.5: Reconnection is a different ticket

A controller that dies is terminal for the ViewModel's lifetime: the future comes from
`PlaybackModule` and is created once. So the honest scope of T11 is *tell the user*, not *recover*.

Recovery means rebuilding the `MediaController` — new future, re-registering the listener, and
deciding what happens to the snapshot in between. That is a real ticket with its own device
protocol, and bundling it here would make an error-reporting slice into a lifecycle one. Record it
as the follow-up.

**Open question for review:** is a message the driver cannot act on worth showing at all, if nothing
can be retried? My answer is yes — "the app is not going to respond" is information, and the
alternative is the current silence, which reads as a broken app rather than a broken connection. But
it is worth arguing.

## File plan

| File | Change |
|---|---|
| `core/playback/.../PlayerTransport.kt` | `onUnavailable` callback, invoked in `withController` |
| `core/playback/.../BasePlayerStateCollector.kt` | `onPlayerUnavailable()` hook; pass it to the transport |
| `app/.../player/PlayerViewModel.kt` | Override the hook; report on `togglePlayPause`'s early return |
| `automotive/.../AutomotivePlayerViewModel.kt` | Override the hook |
| `core/playback/src/test/.../PlayerTransportTest.kt` | The callback fires once per failed operation |
| `docs/aaos-DESIGN.md` | D63 |

## Task 1: The callback

- [ ] `PlayerTransport(controller: () -> MediaController?, onUnavailable: () -> Unit = {})`.
- [ ] One private predicate — `controller()?.takeIf { it.isConnected }` — used by `withController`
      **and** `isPlaying()` (D-T11.0). The callback fires from `withController`'s failure branch
      only.
- [ ] Add `setQueue` and `shufflePlay` to the transport, delegating to the `PlaybackCommands`
      extensions, so the play-entry paths report too (D-T11.4).
- [ ] `BasePlayerStateCollector`: `protected open fun onPlayerUnavailable() {}`, and build the
      transport with `{ onPlayerUnavailable() }`.
- [ ] Extend `PlayerTransportTest`: the callback fires once per failed operation, and `isPlaying()`
      does not fire it.
- [ ] Do **not** claim a test for "a present controller whose guard refuses does not report". That
      needs a real `MediaSession`, which this module has no harness for; the existing suite is
      explicit that it covers the failure branch only. State the gap instead of faking it.

**Acceptance criteria:** the branch reports through one path, and its tests say so.

## Task 2: The two surfaces

- [ ] Mobile overrides `onPlayerUnavailable()` to set the existing `PlayerError`, and adds the same
      report to `togglePlayPause`'s `isPlaying() ?: return` (D-T11.4).
- [ ] The car overrides it the same way; `CarErrorOverlay` already renders `PlayerError`.
- [ ] Check the car's overlay does not fight `CarUxRestrictionsHandler` — the overlay is already
      shown for playback errors while driving, so this adds no new driving-time surface.

**Acceptance criteria:** every transport action on a dead controller produces the message the
surface already uses for a failed connection.

## Task 3: Gate and device

- [ ] Broader gate, `--rerun-tasks`.
- [ ] **Reproduce the original symptom first**, on either surface: `am force-stop` the app while its
      UI is live, then tap play, skip and seek. Before this ticket that is silence; after it, the
      message. This is the first device check in the series that reproduces on demand.
- [ ] While reproducing, confirm **which** branch fires — a null controller or a disconnected one.
      D-T11.0 predicts disconnected, and if the log says otherwise the predicate is answering the
      wrong question.
- [ ] Tap a song from a list in that state: the row must not paint as playing (the second acceptance
      criterion).
- [ ] Confirm the refusals stay quiet: with a live controller, remove the current track from the
      queue and clear a queue of one — neither should show anything.

## Task 4: Docs

- [ ] D63: what reports, what stays silent, and why recovery is a separate ticket.
- [ ] Ticket outcome; file the reconnection follow-up.