# AAOS T11 - Dead Controller Implementation Plan

> **For agentic workers:** T13 already built the machinery. This slice decides what the driver and
> the listener *see* when a transport call finds no player, and wires it once per surface. Do not
> add error handling to the guards that legitimately refuse — see D-T11.2.

**Goal:** A player that cannot reach its service stops looking like a working one. Today every
transport action returns silently and the UI keeps its affordances live; on the car that is a driver
tapping a dead button on the move.

**Ticket:** `docs/tickets/T11-dead-controller-silent-noop.md`

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
Its fourteen unit tests keep working with the default, and one new test asserts the callback fires.

### D-T11.2: Only "no controller" reports. Refusals stay silent.

`PlayerTransport`'s `Boolean` means *reached the controller* (D62). The index, current-item and
queue-size guards return `true` and must raise nothing: a driver tapping remove on the track that is
playing has not hit an error, and `clearQueue` on a queue of one is a refusal the UI already shows by
dimming the button.

This is the finding most likely to be got wrong by someone reading only the ticket title.

### D-T11.3: The message is the one both surfaces already use

`onControllerConnectionFailed` produces "Player Error / Could not connect to playback service" today,
with `isPlaybackError = false` so it routes to the snackbar rather than the expanded player's inline
banner. Reuse it verbatim. A second phrasing for the same condition is how two surfaces start
describing one failure differently.

### D-T11.4: `togglePlayPause` on mobile needs its own line

Mobile calls `isPlaying()` first and returns early on `null`, so it never reaches a transport call
and would never report. Add the report to that early return explicitly. It is the one place the hook
cannot cover, and the one a reader will not notice.

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

- [ ] `PlayerTransport(controller: () -> MediaController?, onUnavailable: () -> Unit = {})`, invoked
      from `withController`'s null branch and from `isPlaying()` when there is no controller.
- [ ] `BasePlayerStateCollector`: `protected open fun onPlayerUnavailable() {}`, and build the
      transport with `{ onPlayerUnavailable() }`.
- [ ] Extend `PlayerTransportTest`: the callback fires for each operation, and — the case worth
      writing — does **not** fire when a controller is present but a guard refuses. That needs a
      controller, so assert it the other way round: with `{ null }`, a refusal cannot happen, so
      assert the count equals the number of calls made.

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
- [ ] Confirm the refusals stay quiet: with a live controller, remove the current track from the
      queue and clear a queue of one — neither should show anything.

## Task 4: Docs

- [ ] D63: what reports, what stays silent, and why recovery is a separate ticket.
- [ ] Ticket outcome; file the reconnection follow-up.