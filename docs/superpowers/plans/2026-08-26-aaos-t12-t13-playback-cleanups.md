# T12 + T13 - Playback Cleanups Implementation Plan

> **For agentic workers:** Two tickets, one branch, because they churn the same imports in the same
> two files. T12 is mechanical. T13 is not: it touches every transport path on both surfaces, and
> the success paths have no automated coverage, so the device pass is not optional.

**Goal:** Finish the pattern T3 and T10 started — one implementation of anything that talks to the
`MediaController`, owned by `BasePlayerStateCollector`, with the two ViewModels keeping only their
own reactions.

**Tickets:** `docs/tickets/T12-shared-queue-command-senders.md`,
`docs/tickets/T13-transport-in-the-collector.md`

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

### D-T13.2: Transport operations report failure; they do not surface it

Each returns a value the caller can act on. The collector does not know about `PlayerError`,
snackbars or `CarErrorOverlay` — D61's rule that the collector stays UI-agnostic still holds. T11
decides what each surface shows.

`Boolean` is enough: every operation has exactly one failure mode, "there is no controller". A
sealed result would be inventing vocabulary for a second case that does not exist.

### D-T13.3: Per-surface differences are decided one at a time, in the open

`skipNext` differs today: the car wraps to index 0 on repeat-all, mobile does not. Do not
silently unify. For each operation where the two surfaces differ, either make it a parameter or
leave it in the ViewModel — and record which, with the reason, in the design doc. An unexplained
behaviour change on mobile is exactly what this series of tickets exists to prevent.

## Task 0: Baseline and branch

- [ ] `main` clean and containing PR #45.
- [ ] Branch; run the focused gate once.

## Task 1: T12 — the two senders move

- [ ] Add to `PlaybackCommands.kt`, beside `sendRestoreState`:
      `MediaController.sendSetQueue(songs: List<Song>, startIndex: Int)` and
      `MediaController.sendShufflePlay(songs: List<Song>)`, both returning the command future.
- [ ] Delete both private copies from each ViewModel; update the call sites.
- [ ] Prune imports per file by checking each symbol — `Bundle`, `SessionCommand`, `toBundle` and
      `PlaybackCommands` may or may not go stale depending on what else the file does.
- [ ] `grep -rn "CMD_SET_QUEUE\|CMD_SHUFFLE_PLAY"` over `--include` Kotlin sources returns only
      `PlaybackCommands.kt` and `PlaybackService.kt`.

**Acceptance criteria:** ~30 lines gone, both apps build, no behaviour change.

## Task 2: T13 — transport moves to the collector

- [ ] Add to `BasePlayerStateCollector`, each returning `Boolean` (false = no controller):
      `togglePlayPause`, `skipNext`, `skipPrevious`, `seekTo`, `cycleRepeatMode`, `skipToQueueItem`,
      `removeFromQueue`, `clearQueue`.
- [ ] Take the bodies from the existing ViewModel methods verbatim; this is a move, not a rewrite.
- [ ] `skipNext` — the car wraps on repeat-all, mobile does not. Decide per D-T13.3 and record it.
      Recommended: make wrapping the shared behaviour, since mobile's `hasNext` already claims a
      next track exists under repeat-all after T10, and a button that claims to work should work.
      That is a mobile behaviour change and must be called out in the device pass.
- [ ] Shuffle stays where it is if it differs — mobile keeps a local `isShuffled` in `PlayerUiState`
      and the car writes the snapshot; check before moving.
- [ ] Each ViewModel method becomes a call plus its own reaction.

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
- [ ] No new detekt baseline entries, no new suppressions. Note both ViewModels are already at
      class-level `TooManyFunctions`; this should *reduce* their method bodies, not their count.

## Task 5: Device pass, both surfaces

The success paths have no automated coverage, so this is where T13 is actually verified.

- [ ] **Car**, `AAOS_AOSP_33_userdebug`: play, pause, skip next and previous, seek, cycle repeat
      through all three modes, shuffle, then queue skip-to, remove and clear — parked. Then driving,
      to confirm the restriction gating still refuses edits.
- [ ] **Mobile**: the same transport set, plus the `skipNext` repeat-all wrap if D-T13.3 changed it.
- [ ] Watch `PlaybackService` start in logcat from the first launch on mobile, per T11's lesson —
      a dead controller makes every one of these look broken for reasons unrelated to this change.

**Acceptance criteria:** every transport action behaves as before on both surfaces, recorded.

## Task 6: Docs

- [ ] `docs/aaos-DESIGN.md`: D62 — transport lives in the collector; the null-controller contract;
      why there is no `MediaController` interface; and each per-surface difference that was unified
      or kept, with its reason.
- [ ] Both ticket outcomes.
- [ ] Note in T11 that its fix now has one place to live.
