# AAOS T14 - Controller Reconnection Implementation Plan

> **For agentic workers:** this changes a contract every surface depends on. Read the ownership
> section before touching `PlaybackModule`, and do not start by writing a retry loop — the first
> thing to fix is that the app releases its own controller and can never build another.

**Goal:** A playback controller that is gone — because the session died, or because the app released
it — can be replaced without restarting the process, so T11's message becomes a last resort rather
than a dead end.

**Ticket:** `docs/tickets/T14-controller-reconnection.md`

**Verification command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest :app:assembleDebug`

**Broader gate:** the usual set, plus device passes on both surfaces.

## Verified Root Cause

**Verified from code:**

1. `PlaybackModule.provideMediaControllerFuture` is `@Singleton`: one
   `ListenableFuture<MediaController>` for the whole process, built once by
   `MediaController.Builder(...).buildAsync()`.
2. Both ViewModels inject that future and hand it to `BasePlayerStateCollector`, whose
   `connectController()` only attaches a listener to it. Nothing ever asks for another.
3. **`releaseController()` calls `MediaController.releaseFuture(mediaControllerFuture)` on that
   shared singleton, and both ViewModels call it from `onCleared()`.**

Fact 3 is the one that makes this ticket urgent, and it is worse than the ticket describes. A
ViewModel dying does not just drop its own connection — it releases the controller *for the
process*. The next ViewModel to be created injects the same, now released, future. It resolves to a
non-null `MediaController` whose `isConnected` is false, which is precisely the state T11 reports
and cannot recover from.

**Hypothesis this makes testable (was open in T11):** the 2026-08-26 report — drawn player, no
session, silent buttons — may be exactly this, not a service death at all. An Activity finishing
while the process lives is an ordinary event: back out of the app, come back, and the controller is
already released. T17's harness can now prove or disprove it without a device.

**Assumption, unverified:** whether Media3 resolves an already-released future to a disconnected
controller or throws. The first task settles it with a test rather than reasoning.

## Design Principles

1. **A ViewModel may end its own use of a controller without ending everyone's.**
2. **Reconnection is attempted for a user action, not on a timer.** No background retry loops.
3. **One attempt in flight at a time.** Repeated taps must not start repeated connections.
4. **T11's message stays**, as what the user sees when a reconnect fails or is not attempted.
5. **No behaviour change for a healthy controller.**

## Proposed Implementation

### Step 1 — Prove the failure mode, before changing anything ✅ **done**

`SharedControllerFutureTest`, two tests, both passing:

- **`releasingTheSharedFuture_leavesTheNextConsumerHoldingADeadController`** — the future hands the
  *same* `MediaController` instance to the next caller, and after `MediaController.releaseFuture(...)`
  that instance reports `isConnected == false`. It does not throw, and it does not rebuild.
- **`aDeadControllerRefusesEveryCommandAndReports`** — in that state every transport command refuses
  and raises T11's report.

**So the hypothesis is now a fact.** One ViewModel reaching `onCleared()` poisons the controller for
the whole process, and the next ViewModel injects the corpse. Nothing has to crash, no service has to
die, and the user does nothing unusual: back out of the app while the process lives, come back, press
play.

That also makes it the most likely mechanism behind the 2026-08-26 report — a drawn player with a
dead controller — which T11 recorded as an open question between "null" and "stale". It is a third
answer, and the only one that needs no crash to explain the symptom.

Two facts for the implementation: the future is **idempotent** (same instance every `get()`), and
release is **terminal** for it. So recovery cannot mean "ask the future again"; it means building a
new one, which is exactly what Step 2 has to make possible.

### Step 2 — `:core:playback`: an owner for the connection

Replace the injected `ListenableFuture<MediaController>` with something that can be asked again. The
smallest shape that fits: a `@Singleton` `ControllerConnection` in `:core:playback` that

- builds a future on demand and caches it,
- hands it out to collectors,
- releases and drops the cached one when the *last* consumer is gone (reference count) or never
  releases at all and lets process death do it,
- exposes `reconnect()`, which drops the current future and builds a fresh one.

*Why not a plain non-singleton `@Provides`:* two collectors would each build a controller and neither
would know about the other's, which is a different bug rather than a fix. Reference counting is the
smallest thing that makes "my ViewModel is done" distinct from "nobody is using playback".

*Alternative weighed and rejected:* drop `releaseController()` entirely and let the process own the
controller for its lifetime. Fewer moving parts — but a `MediaController` holds a binding to
`PlaybackService`, so never releasing means the service can never stop, including the
`onTaskRemoved` → `stopSelf()` path that exists precisely to let it go. Reference counting keeps that
working; five tests hold the rules, including the unbalanced-release case.

**Done.** `ControllerConnection` is a `@Singleton` over `acquire()` / `release()` / `reconnect()`,
tested against a real `MediaSession` rather than `PlaybackService` (D64). `reconnect()` exists
because Step 1 proved a released future is terminal and idempotent: it can only be replaced, never
revived.

### Step 3 — `BasePlayerStateCollector`: connect through the owner

`connectController()` asks the owner for the current future; `releaseController()` tells the owner
this consumer is finished rather than releasing the shared future itself.

*Preserve:* the existing `onControllerConnected` / `onControllerConnectionFailed` hooks and the
position poller's lifecycle, which is keyed to `collectorScope`.

**Done.** The collector takes a `ControllerConnection` instead of a future; `connectController()`
acquires, `releaseController()` releases *this consumer*. `PlaybackModule` no longer provides a
future at all — Hilt builds the connection from its `@Inject` constructor — so there is no shared
future left for anyone to release.

One cost, taken deliberately: `RestoredSnapshotTest` used to construct a collector from a
`SettableFuture` with no Android at all, and now needs Robolectric, because a `ControllerConnection`
needs a `Context` and a `SessionToken` to exist. Its seven assertions are unchanged and it still
never calls `connectController()`, so nothing connects — the session it builds is only there to hand
over a token.

### Step 4 — Reconnect on a failed command, once

When `PlayerTransport` finds no connected controller (T11), the collector asks the owner to
reconnect before reporting. If the reconnect succeeds, the command is **not** retried automatically —
the user taps again against a working player. If it fails, `onPlayerUnavailable()` fires as it does
today.

*Why not retry the command:* a skip that silently happens two seconds late is worse than one that
visibly did nothing, and replaying a queue-mutating command against a freshly connected session is a
correctness question this ticket should not open.

*Guard:* one attempt in flight; further failures while it runs are silent, because the first one
already decided what happens.

**Done.** `onControllerLost()` on the collector, behind an `AtomicBoolean`. Four tests over T17's
harness: a command against a lost controller rebuilds the connection and the *next* command works; a
successful reconnect tells the surface nothing; three taps during one attempt produce one attempt;
and a reconnect with no session to reach reports exactly once.

Two implementation notes. The position poller is **not** restarted on reconnect — the original loop
reads `controller` each tick and picks up the new one, so starting another would double it. And
`BasePlayerStateCollector` hit the same 16-function ceiling `PlayerTransport` did; `readQueue` and
`awaitResultCode` moved to file level, both touching no class state.

### Step 5 — Both ViewModels

No API change beyond construction, and `onCleared()` still calls `releaseController()` — which now
means "I am done" rather than "release the process's controller".

**One gap the plan did not foresee.** Mobile's `togglePlayPause` opens with `isPlaying()`, a query,
which is silent by design and therefore cannot trigger a rebuild. It reported failure directly, so
after T14 the play button would have been the one control on either surface that gave up instead of
recovering. It now asks for the toggle and lets it fail, which routes through the same reconnect
every other control gets.

Both `onPlayerUnavailable()` overrides say in their KDoc that they are reached only after a rebuild
has failed — a controller that can be replaced is replaced silently, and the user never learns it
happened.

## Tests

With T17's harness, all of these are JVM tests:

| Test | Proves |
|---|---|
| release the future, then use it again | Step 1's fact — the failure mode this ticket exists for |
| one consumer releases, another still commands | a ViewModel dying does not kill playback for the rest |
| reconnect after release yields a connected controller | the recovery works at all |
| command → unavailable → reconnect → command works | the user-visible sequence |
| two failures do not start two connections | Principle 3 |
| reconnect fails → `onPlayerUnavailable` fires once | T11's path still works as the fallback |

## Gate result, 2026-08-26

Re-run from scratch: `:core:playback` 81 tests (72 before Task 2, 55 before T11), `:automotive` 171,
`:core:data` 63 — **315 total, zero failures** — plus detekt, `lintOemDebug` at zero errors, both
automotive flavors and `:app:assembleDebug`. `detekt-baseline.xml` untouched, no `@Suppress` added.

Eleven files, +796/−64. `ListenableFuture<MediaController>` now appears in exactly one production
file, `ControllerConnection`; nothing else in the codebase holds a controller future.

## Manual Verification

- **Mobile:** play something, finish the Activity (back out until the app closes, process alive),
  reopen, and press play. Today: nothing happens, per fact 3. After: it plays.
- **Car:** the same, plus a driving-state pass to confirm no new surface appears mid-drive.
- Capture `dumpsys media_session` and the app pid at each step; the pid must not change, or the test
  has proved nothing about reconnection.

## Out of Scope

- Restore behaviour, which already treats a null controller as "do nothing".
- T16's availability-in-UI-state; T14 changes when the unavailable state ends, not how it looks.
- Automatic retry of the command that found the connection dead (Step 4).

## Risks

- **Reference counting is state.** Getting it wrong means either a controller released while a
  surface still needs it, or one that never goes away. The "never release" alternative in Step 2
  trades that risk for a leak Media3 already tolerates — argue it in review.
- **Two collectors, one connection.** Only one surface exists per device today, but the code allows
  both; whatever is chosen must not assume a single consumer.
- **Reconnect storms** if Principle 3 is implemented loosely.

## Acceptance Criteria

1. Given a ViewModel is cleared, when another still holds the connection, then its commands still
   reach the player.
2. Given the controller is released or disconnected, when a user action finds it unavailable, then a
   reconnect is attempted once, and a subsequent action reaches the player.
3. Given the reconnect fails, then the user is told once, and no further attempt is started until
   the next user action.
4. Given a healthy controller, then nothing about today's behaviour changes.
5. Given the app is backed out of and reopened without the process dying, then playback controls work.

## Open Questions

1. Reference counting versus never releasing (Step 2). The second is smaller; the first is tidier.
   Decide in review, not in code.
2. Should a *successful* reconnect surface anything to the user, or recover silently? Silent is
   proposed, on the grounds that a message about something that just fixed itself is noise.
