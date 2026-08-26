# T12 + T13 - Playback Cleanups Implementation Plan

> **For agentic workers:** Two tickets, one branch, because they churn the same imports in the same
> two files. T12 is mechanical. T13 is not: it touches every transport path on both surfaces, and
> the success paths have no automated coverage, so the device pass is not optional.

**Goal:** Finish the pattern T3 and T10 started — one implementation of anything that talks to the
`MediaController`, owned by `BasePlayerStateCollector`, with the two ViewModels keeping only their
own reactions.

**Tickets:** `docs/tickets/T12-shared-queue-command-senders.md`,
`docs/tickets/T13-transport-in-the-collector.md`

**External review:** `codex exec`, 2026-08-26 — three blockers and six should-fixes, all folded in:
a wrong operation inventory, a call-site count that double-counted T12's senders, and detekt's class
threshold, which is why transport is now its own class rather than more methods on the collector.

**Verification command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest`

**Broader gate:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest
:core:data:testDebugUnitTest detekt :automotive:lintOemDebug :automotive:assembleOemDebug
:automotive:assemblePlaystoreDebug :app:assembleDebug`, plus a device pass per surface.

## Current baseline

`main` after PR #45. `BasePlayerStateCollector` owns the controller, the snapshot, and — since T10
— `applyRestored` and `restoreIfIdle`. `PlaybackCommands.kt` owns `sendRestoreState`.

The two ViewModels still hold: byte-identical `sendSetQueueCommand` / `sendShufflePlayCommand`
(T12), and seventeen transport methods that each open with `stateCollector.controller ?: return`
(T13).

## Decisions

### D-T13.1: No interface over `MediaController`

Recorded in the ticket and repeated here because it is the tempting move. A seam means an interface
of ~17 members, a delegating implementation and a fake, invented for one consumer. The defect that
keeps recurring is the **null-controller branch**, and that becomes testable simply by living on the
collector — which T10 proved is constructible in a unit test with a `SettableFuture` that never
completes. Build the seam when a second consumer needs to assert what was sent.

**The honest limit:** this buys the failure branch, not the success path. Asserting *what was sent*
still needs a seam or a real `MediaSession`/`SimpleBasePlayer` harness, and `:core:playback` has
only JUnit and coroutines test deps today — Robolectric lives in `:automotive`. Do not present this
ticket as making transport tested; it makes the branch that keeps biting tested and leaves the rest
device-verified, exactly as it is now.

### D-T13.2: Transport lives in its own class, not on the collector

`BasePlayerStateCollector` has ten member functions and detekt's `thresholdInClasses` is 16. Adding
eight transport methods puts it at eighteen, and this project does not answer a threshold with a
suppression (D23).

So: a `PlayerTransport` class over a `() -> MediaController?` supplier, owning the operations and
exposed by the collector as a property — `stateCollector.transport.skipNext()`. It is also better
than piling onto the collector: the transport is constructible in a test with a supplier that
returns null, without standing up a collector at all.

### D-T13.3: `Boolean` means "reached the controller", nothing more

`false` when there is no controller, `true` when the call was made. It does **not** mean the
operation had an effect: `skipToQueueItem` already ignores an out-of-range index, `removeFromQueue`
ignores the current item, `clearQueue` ignores a queue of one, and `skipNext` at the end of a
non-repeating queue does nothing. Those guards move unchanged and stay silent — they are correct
refusals, not failures, and T11 is about the case where the player is gone.

Say that in the KDoc, or the first reader wires an error dialog to a driver tapping remove on the
current track.

The transport knows nothing of `PlayerError`, snackbars or `CarErrorOverlay` — D61's rule that this
layer stays UI-agnostic holds. T11 decides what each surface shows.

### D-T13.4: Per-surface differences are decided one at a time, in the open

**Correction from review:** an earlier draft claimed `skipNext` differs between the surfaces and
proposed unifying it. It does not — both wrap to index 0 when `repeatMode == REPEAT_MODE_ALL` and
`mediaItemCount > 0`, byte for byte. No mobile behaviour change there, and that `mediaItemCount > 0`
guard must survive the move: `hasNextTrack()` returns true for repeat-all without checking the queue
is non-empty.

What genuinely differs, and must be decided per operation rather than merged:

| Operation | Difference | Disposition |
|---|---|---|
| `togglePlayPause` | mobile checks offline state and the download manager before `play()` and can emit a `PlayerError`; the car just toggles | transport moves the toggle; mobile keeps its pre-flight |
| `seekTo` | mobile writes the position optimistically into `_uiState`; the car does not | transport moves the seek; the optimistic write stays mobile's |
| `toggleShuffle` | mobile's optimistic flag lives in `PlayerUiState`, the car's in `PlaybackSnapshot` | transport sends the command; each keeps its flag until a ticket unifies where `isShuffled` lives |
| `dismiss` (mobile only) | `stop()` + `clearMediaItems()`, then resets `PlayerUiState` | transport gets `stopAndClear()`; the state reset stays mobile's |
| offline-buffering pause (mobile only) | `controller?.pause()` from `handleOfflineBuffering` | uses the shared `pause()` |

Record each row in the design doc with its reason. An unexplained behaviour change on mobile is what
this series of tickets exists to prevent.

## Task 0: Baseline and branch

- [ ] `main` clean and containing PR #45.
- [ ] Branch; run the focused gate once.

## Task 1: T12 — the two senders move

- [x] Add to `PlaybackCommands.kt`, beside `sendRestoreState`:
      `MediaController.sendSetQueue(songs: List<Song>, startIndex: Int)` and
      `MediaController.sendShufflePlay(songs: List<Song>)`, both returning the command future.
- [x] Delete both private copies from each ViewModel; update the call sites.
- [x] Prune imports per file by checking each symbol — `Bundle`, `SessionCommand`, `toBundle` and
      `PlaybackCommands` may or may not go stale depending on what else the file does.
- [x] `grep -rn "CMD_SET_QUEUE\|CMD_SHUFFLE_PLAY"` over `--include` Kotlin sources returns only
      `PlaybackCommands.kt` and `PlaybackService.kt`.

**Acceptance criteria:** ~30 lines gone, both apps build, no behaviour change.

**Result.** 52 deletions against 41 insertions across three files. The constants grep returns only
`PlaybackCommands.kt` and `PlaybackService.kt`. `toBundle` went stale in both ViewModels — it was
only there for the two bundles that moved — and `Song` had to be imported into `PlaybackCommands.kt`
for the new signatures. Each call site is now `stateCollector.controller?.sendSetQueue(...)`, which
keeps the silent no-controller behaviour the private copies had; T13 is where that changes.

## Task 2: T13 — transport moves to the collector

- [ ] Create `PlayerTransport` in `:core:playback` over a `() -> MediaController?` supplier, each
      operation returning `Boolean` per D-T13.3: `togglePlayPause`, `skipNext`, `skipPrevious`,
      `seekTo`, `toggleRepeatMode`, `toggleShuffle`, `skipToQueueItem`, `removeFromQueue`,
      `clearQueue`, `stopAndClear`. The names are the ViewModels' own — `toggleRepeatMode`, not
      `cycleRepeatMode`.
- [ ] Expose it from `BasePlayerStateCollector` as a property built from its own controller.
- [ ] Take the bodies verbatim, including every index and queue-size guard. A move, not a rewrite.
- [ ] Each ViewModel method becomes a transport call plus its own reaction, per D-T13.4's table.
- [ ] Thirteen `stateCollector.controller ?: return` opens remain once T12 has removed the four
      command senders — not seventeen, which counted those.

**Acceptance criteria:** no transport method in either ViewModel reaches
`stateCollector.controller`.

## Task 3: Tests for the branch that keeps biting

- [ ] Extend `RestoredSnapshotTest`'s harness, or add a sibling, constructing a collector with an
      unresolved future: every transport operation returns false and mutates no snapshot state.
- [ ] Mutation-check two of them: make one return `true` unconditionally and confirm a named test
      fails.

**Acceptance criteria:** the no-controller branch is covered by name for every operation — the
first automated coverage any transport path has ever had.

## Task 4: Gate

- [ ] Broader gate, `--rerun-tasks`.
- [ ] No new detekt baseline entries and no new suppressions — which is why transport is its own
      class (D-T13.2). Check `PlayerTransport` against `thresholdInClasses: 16`: ten operations fits,
      but only just.
- [ ] Both ViewModels are already at class-level `TooManyFunctions`; this reduces their bodies, not
      their method count.

## Task 5: Device pass, both surfaces

The success paths have no automated coverage, so this is where T13 is actually verified.

- [ ] **Car**, `AAOS_AOSP_33_userdebug`: play, pause, skip next and previous, seek, repeat through
      all three modes, shuffle, then queue skip-to, remove and clear — parked. Then driving, to
      confirm queue edits are still refused. Gating is a **UI** contract: `CarQueueScreen` disables
      the controls, neither ViewModel nor transport reads `UxRestrictionState`, and the transport
      must not learn about it.
- [ ] **Mobile**: the same transport set, plus `dismiss()` and the offline-buffering pause — both
      mobile-only paths that now route through the shared transport.
- [ ] Watch `PlaybackService` start in logcat from the first launch on mobile, per T11's lesson —
      a dead controller makes every one of these look broken for reasons unrelated to this change.

**Acceptance criteria:** every transport action behaves as before on both surfaces, recorded.

## Task 6: Docs

- [ ] `docs/aaos-DESIGN.md`: D62 — transport lives in the collector; the null-controller contract;
      why there is no `MediaController` interface; and each per-surface difference that was unified
      or kept, with its reason.
- [ ] Both ticket outcomes.
- [ ] Note in T11 that its fix now has one place to live.
