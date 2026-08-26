# T11 — Disconnected or missing MediaController makes playback actions fail silently

Recommended title change from "A dead controller leaves a fully drawn player whose buttons do
nothing". The old title describes the symptom; this one names the condition, and the distinction
matters because there are **two** conditions and the first draft of this plan only handled one.

### Summary

A user-initiated playback action can reach a `MediaController` that is missing or no longer
connected. Nothing is dispatched, nothing is reported, and the UI keeps its track metadata, progress
and live controls — on mobile it also paints an optimistic playing state for a song that never
reached a player.

T11 makes command-time availability an explicit check (`controller != null && controller.isConnected`),
routes one report through the existing error channels, closes the two call paths that bypass
`PlayerTransport`, and fixes the mobile layering that would render that report underneath the
expanded player.

It does not reconnect anything.

## Verified Root Cause

**Verified from code:**

1. `PlaybackModule.provideMediaControllerFuture` builds **one** `@Singleton`
   `ListenableFuture<MediaController>` with `MediaController.Builder(context, sessionToken).buildAsync()`
   and never calls `setListener`. **There is no `MediaController.Listener` and no `onDisconnected`
   callback anywhere in the repository.**
2. `BasePlayerStateCollector.controllerListener` is a `Player.Listener`, which is a different
   interface and carries no disconnect signal. The collector assigns `controller = mc` once, in
   `connectController`'s future listener, and never nulls it. `releaseController()` only calls
   `MediaController.releaseFuture(...)`.
3. `PlayerTransport.withController` returns `false` **only** when the supplier returns `null`. A
   non-null controller is taken as usable, and every refusal guard (`skipToQueueItem` on a bad index,
   `removeFromQueue` on the current item, `clearQueue` on a queue of one) returns `true` (D62).
4. `playSong` and `shufflePlay` on **both** surfaces bypass the transport: they call
   `stateCollector.controller?.sendSetQueue(...)` / `sendShufflePlay(...)` and then write optimistic
   state — mobile `playerMode = Expanded, isPlaying = true`, the car
   `updateSnapshot { isPlaying = true }` — with no check that anything was dispatched.
5. `transport.isPlaying()` has exactly one caller, `PlayerViewModel.togglePlayPause`, which is a user
   action. No polling, collection or recomposition path calls it.
6. `NyasaPlayerApp` passes the snackbar host to `Scaffold(snackbarHost = ...)` and renders
   `GlobalPlayerLayer` **after** the `Scaffold`, inside the same `Box`. The expanded player fills the
   screen, so a snackbar raised while it is open is behind it.
7. There is no `android:process` anywhere in the repo: `PlaybackService` runs in the app's process.
8. The car renders any `playerState.error` through `CarErrorOverlay` above the full player and the
   queue; `isPlaybackError = false` only changes its icon and Retry stays hidden unless
   `isRetryable`. The overlay blocks the controls underneath it, so repeated taps cannot stack.

**Assumption, not verified — and the plan must prove it rather than assert it:**

The 2026-08-26 report (no `ServiceRecord`, no `MediaSession`, fully drawn player, silent buttons,
nothing in logcat) was attributed in the ticket to a stale non-null controller. **That was never
confirmed.** `isConnected` was never read on a live object. Two candidates produce identical
symptoms:

- **(a) Stale controller** — the session died while the process lived, leaving a non-null
  disconnected object. Fact 3 says every command would pass through it silently.
- **(b) No controller** — the future failed or never resolved, so `controller` is null.
  `onControllerConnectionFailed()` *does* already raise an error for this on both surfaces — but on
  mobile fact 6 means that error is invisible whenever the expanded player is open.

Both are real, both produce the reported experience, and the same fix covers both. The plan is
written for both, and verification is written to say which one was actually hit.

**Reproduction is harder than the ticket claims.** `am force-stop` kills the Activity along with the
service (fact 7), so it cannot demonstrate a live UI over a dead player. `am stopservice` was tried
on the AAOS emulator during this planning session: it printed `Service stopped`, and the session
survived with two `ServiceRecord`s still listed, because the controller is still bound. Neither is a
demo. See **Manual Verification**.

## Design Principles

1. **Availability is asked at command time, never assumed from a cached field.**
2. **Passive reads stay silent.** A query must not produce a user-facing error.
3. **Refusals are not failures.** The D62 guards return "attempted"; they must never raise anything.
4. **Optimistic UI requires dispatch.** No writing "playing" for a command that was not sent.
5. **The transport knows nothing about UI.** It reports through the existing hook mechanism.
6. **One report per user action**, not one per internal call.

## Proposed Implementation

### Step 1 — `:core:playback`, `PlayerTransport`: one availability predicate

Replace the null check with a private `connectedController(): MediaController?` returning
`controller()?.takeIf { it.isConnected }`, used by `withController` **and** `isPlaying()`.

*Why here:* it is the single boundary every command already passes through after T13, and the
condition is about the controller, not about either surface.

*Preserve:* every refusal guard inside the operations, unchanged and still returning `true`.

### Step 2 — `:core:playback`, `PlayerTransport`: report unavailability once, from commands only

Add a second constructor parameter `onUnavailable: () -> Unit = {}`, invoked from `withController`'s
failure branch. `isPlaying()` uses the same predicate and **does not** invoke it (principle 2, fact 5).

*Why here:* one place, so an operation added later cannot forget.

### Step 3 — `:core:playback`, `BasePlayerStateCollector`: expose the signal

Add `protected open fun onPlayerUnavailable() {}` beside the four hooks that already exist, and build
the transport with `PlayerTransport({ controller }, { onPlayerUnavailable() })`.

*Why here:* it matches the existing pattern exactly; a fifth hook is not a new architecture. If the
hook count later becomes debt, that is a follow-up, not this ticket.

*Preserve:* the transport is constructed in a property initialiser, but the lambda only captures
`this` and is never invoked during construction, so there is no initialisation-order hazard.

### Step 4 — `:core:playback`: move the two play-entry senders behind the transport

Add `setQueue(songs, startIndex)` and `shufflePlay(songs)` to `PlayerTransport`, delegating to the
existing `MediaController.sendSetQueue` / `sendShufflePlay` extensions in `PlaybackCommands.kt`.

*Why here:* fact 4 is the path that produces the false "playing" state, and it is the ticket's second
acceptance criterion. It also finishes T12/T13 — after this, no ViewModel touches
`stateCollector.controller` at all.

### Step 5 — `:app` and `:automotive` ViewModels: react

- Override `onPlayerUnavailable()` on both to raise the existing `PlayerError`
  ("Player Error" / "Could not connect to playback service", `isPlaybackError = false`), the same
  values `onControllerConnectionFailed` already uses.
- `playSong` / `shufflePlay`: write optimistic state **only** when the transport call returned `true`.
- Mobile `togglePlayPause`: `transport.isPlaying() ?: run { onPlayerUnavailable(); return }` — the one
  path a silent query would otherwise swallow.
- Mobile: raise the unavailable error only when no error is already showing, so repeated taps do not
  re-trigger the snackbar (its `LaunchedEffect` clears the error after showing it).

### Step 6 — `:app`, `NyasaPlayerApp`: make the message visible

Move `AppSnackbarHost` out of `Scaffold(snackbarHost = ...)` and into the outer `Box`, rendered after
`GlobalPlayerLayer`, keeping the bottom-bar padding it has today.

*Why here:* fact 6 — an invisible error fails the requirement. This fixes every non-playback error,
not only this one.

*Preserve:* the existing routing rule; do not start showing non-playback errors in the expanded
player's inline banner, which would change semantics for errors beyond T11.

## Implementation notes, from doing it

Three things the plan did not predict:

1. **`PlayerTransport` blew the class threshold twice.** It was at 14 functions; `setQueue`,
   `shufflePlay` and the availability predicate took it to 17, and detekt's `thresholdInClasses: 16`
   is **inclusive** — 16 fails too. The predicate and the dispatch helper moved to file level, and
   the three queue mutations moved into a `QueueTransport` reached as `transport.queue`. That is the
   split this plan named as the response to the ceiling, arriving one ticket earlier than expected.
2. **The trailing-lambda trap.** `PlayerTransport` now takes `(controller, onUnavailable)`, so
   `PlayerTransport { null }` binds the lambda to *the callback*, not the controller — silently
   giving a transport with no controller supplier. Every construction site now names its arguments,
   and the test file says why.
3. **`NyasaPlayerApp` hit `LongMethod`** at 61 lines once the snackbar host moved into the `Box`.
   The call is a single line and the explanation lives on `AppSnackbarHost`'s KDoc, which is a better
   home for it anyway.

## Transport Contract

`PlayerTransport` operations return `Boolean`:

- `true` — **the controller was connected when the command was attempted.** The command was handed
  to Media3, or a documented guard inside the operation declined to act (bad index, current item,
  queue of one). It is *not* an acknowledgement that playback changed, and the session can in
  principle disappear between the check and the call.
- `false` — **the controller was missing or disconnected.** Nothing was attempted, and
  `onUnavailable` has fired exactly once.

`isPlaying(): Boolean?` returns `null` for the same unavailable condition and raises nothing.

**Boolean is kept rather than a `TransportResult` sealed type.** The distinction between *dispatched*
and *ignored* is only load-bearing where optimistic state is written, and the two operations that
write it — `setQueue` and `shufflePlay` — have no guard that can ignore: for them `true` means
dispatched, unambiguously. The operations where `true` can mean "ignored" (`skipToQueueItem`,
`removeFromQueue`, `clearQueue`) have no caller that reads the value, and inventing a three-case type
for zero readers is the expansion this brief warns against. **The trigger to introduce it:** the first
caller that must distinguish a refusal from a dispatch. Documented per operation in the KDoc so the
ambiguity is stated rather than discovered.

## Error Reporting Flow

```
user action → ViewModel → PlayerTransport
                              │ controller null or !isConnected
                              ▼
                        onUnavailable()
                              │
                   BasePlayerStateCollector.onPlayerUnavailable()
                              │
              ┌───────────────┴───────────────┐
   PlayerViewModel                   AutomotivePlayerViewModel
   PlayerError(isPlaybackError=false)          same
              │                                 │
   snackbar, above GlobalPlayerLayer   CarErrorOverlay, above everything
```

The car needs no UI change (fact 8). Mobile needs Step 6.

## Optimistic State Rules

May be written: after a transport call returned `true`.

Must not be written: when it returned `false`; when no call was made because a query returned `null`.

Unchanged: the optimistic writes that follow a *successful* dispatch, including mobile's
`playerMode = Expanded` and both surfaces' `isShuffled` flags, which T13 already gated on the return.

## Lifecycle / Disconnect Handling

**Deferred, deliberately.** Clearing `controller` on disconnect would make the field honest, but fact
1 says there is no `MediaController.Listener` today, and the only place to attach one is
`MediaController.Builder` inside `PlaybackModule` — which builds a `@Singleton` future shared by both
ViewModels and holds no reference to either collector. Wiring that means answering who owns the
future, what happens to the listener when a ViewModel dies, and whether a second collector may
observe a disconnect meant for the first. That is the reconnection ticket, not this one.

The command-time predicate does not need it: it asks the controller directly, and so also covers the
window between a session dying and any callback arriving.

**No error may be raised by teardown.** `onCleared()` → `releaseController()` runs no transport
command, and the hook fires only from a command path, which only a user action reaches.

## Mobile Snackbar Layering

Verified: `Scaffold(snackbarHost = { AppSnackbarHost(...) })` at `NyasaPlayerApp.kt:58`,
`GlobalPlayerLayer(` at `NyasaPlayerApp.kt:83`, both inside one `Box`. Later siblings draw on top, and
the expanded player fills the screen.

Smallest fix: move the host into the `Box` after `GlobalPlayerLayer`. Do not restructure the
`Scaffold` otherwise, and do not change which errors route to the snackbar.

## Tests

In `:core:playback`, `PlayerTransportTest` (the harness is `PlayerTransport { null }`; no
`MediaController` is constructible in a unit test — `@DoNotMock`, package-private constructor):

| Test | Proves |
|---|---|
| every command with `{ null }` returns false and fires `onUnavailable` once | the null branch reports, once per call |
| `isPlaying()` with `{ null }` returns null and fires nothing | passive reads stay silent (principle 2) |
| `setQueue` / `shufflePlay` with `{ null }` return false | the play-entry paths report |
| callback count equals command count across a sequence | no double-reporting |

**Stated gap, not faked:** the `isConnected == false` branch and every "connected controller"
behaviour — including "a guard refuses and nothing is reported" — cannot be unit-tested here. That
needs a real `MediaSession` or a `SimpleBasePlayer` harness, which `:core:playback` does not have
(JUnit and coroutines only; Robolectric lives in `:automotive`). Those rows are covered by the manual
pass below. Building that harness is a follow-up.

`:app` and `:automotive` have no ViewModel-level test for this: both ViewModels need a
`ListenableFuture<MediaController>` to construct. The verification command is therefore *not*
sufficient on its own, and the plan does not pretend otherwise.

## Manual Verification

**Neither of the obvious kill commands works** — both were tried:

- `am force-stop` kills the Activity too (fact 7), so there is no live UI to demonstrate against.
- `am stopservice --user 10 com.example.nyasaplayer/...core.playback.PlaybackService` prints
  `Service stopped`, but `dumpsys activity services` still lists two `ServiceRecord`s and the session
  survives, because the controller keeps it bound.

So the state must be produced from inside the app, and the honest options are:

1. **Instrumented probe (recommended).** A throwaway debug build that releases the controller — or
   nulls the collector's field — behind a hidden trigger, exactly as T3's race probe was built and
   reverted. Then: tap play, skip, seek, and tap a song row. Expected before the fix: silence and, on
   mobile, a row that paints as playing. After: the unavailable message on both surfaces, and no
   optimistic playing state. Revert the probe and re-run the shipped build.
2. **Opportunistic capture.** The condition was seen once in the wild on 2026-08-26. Add a log line
   at the `onUnavailable` boundary recording `controller == null` versus `isConnected == false`, ship
   it, and let the next occurrence say which branch fired. This is what settles the open assumption in
   **Verified Root Cause**.

Record, for whichever branch fires: the `dumpsys media_session` count for the package, the
`ServiceRecord` count, the app pid before and after, and the UI state before the first tap.

## Out of Scope

- Reconnecting or rebuilding the controller, and any change to the singleton future's ownership.
- Clearing `controller` on disconnect (see Lifecycle).
- Disabling or dimming transport controls based on availability.
- Removing an already-restored stale player from the screen.
- Restore behaviour (T3, T10) and the D62 refusal guards.
- `isCommandAvailable(...)` capability checks: none of the affected operations consult them today, and
  adding them would widen the ticket.

## Risks / Edge Cases

- **The check is not a guarantee.** A session can vanish between `isConnected` and the Media3 call.
  `true` therefore means *attempted while connected*, and the contract says so.
- **Repeated taps.** The car's overlay blocks the controls beneath it. Mobile clears the error after
  showing it, so Step 5's "only if no error is showing" guard is what prevents a snackbar per tap.
- **A legitimate refusal must stay quiet.** The most likely regression is wiring the report to
  `false` *and* to the guards; the guards return `true` and must keep doing so.
- **Double reporting** if `isPlaying()` were made to report as well as `togglePlayPause` — the reason
  it stays side-effect-free.
- **Behaviour for a healthy controller must be identical.** Every operation keeps its body; only the
  predicate in front changes.

## Acceptance Criteria

1. Given `controller == null`, when any transport command runs, then it returns `false`, the
   surface's unavailable error is raised exactly once, and no optimistic state is written.
2. Given a non-null controller with `isConnected == false`, the same holds — verified by the manual
   probe, since it is not unit-testable here.
3. Given a connected controller, every operation behaves exactly as it does today, including the
   refusal guards, which raise nothing.
4. Given an unavailable controller, when a song row is tapped, then no row paints as playing on either
   surface.
5. Given an unavailable controller and the mobile expanded player open, the message is visible above
   the player.
6. Given `isPlaying()` is called with an unavailable controller, it returns `null` and raises nothing.
7. Given repeated taps while unavailable, at most one message is showing at a time on each surface.

## Follow-up Tickets

- **Controller reconnection** — a future factory, listener lifecycle, poller restart, and what the
  snapshot shows meanwhile. The real fix for the condition T11 only reports.
- **Clear `controller` on disconnect** — needs a `MediaController.Listener` and an answer to future
  ownership; makes the field honest rather than the callers careful.
- **Availability in UI state** — carry it in `PlaybackSnapshot` and dim the controls, so the answer to
  "why is nothing happening" is visible before the tap.
- **A `MediaController` test seam or `SimpleBasePlayer` harness** — the reason half this ticket's
  table is manual.
- **Hook count on `BasePlayerStateCollector`** — five after this. Worth a look if a sixth appears.

## Open Questions

1. Which branch produced the 2026-08-26 report — null or disconnected? Not answerable from the
   repository; the log line in Manual Verification option 2 is how to settle it.
2. Whether Media3 ever surfaces a disconnected controller through `Player.Listener` in a way the
   collector could already observe. Not evident from the code; worth a look at `MediaController`'s
   sources before building the reconnection ticket.
