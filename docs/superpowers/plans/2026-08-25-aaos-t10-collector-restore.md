# AAOS T10 - Restore Policy in the Collector Implementation Plan

> **For agentic workers:** The design argument is settled in the ticket; do not re-litigate it here.
> This is a move, not a redesign. Both surfaces must end up with identical restore behaviour, and
> the only intended behaviour *change* is the one named in D-T10.3.

**Goal:** One implementation of "apply a restored session", owned by `BasePlayerStateCollector`,
so the three divergences T3 had to comment on become unreachable rather than scheduled.

**Ticket:** `docs/tickets/T10-restore-policy-in-the-collector.md`

**Verification command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest`

**Broader gate:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest
:core:data:testDebugUnitTest detekt :automotive:lintOemDebug :automotive:assembleOemDebug
:automotive:assemblePlaystoreDebug :app:assembleDebug`, plus a device pass per surface.

## Current baseline

Start from `main` after T10's proposal merged (PR #44).

- `BasePlayerStateCollector` owns `controller`, the `PlaybackSnapshot` flow, `updateSnapshot`,
  `syncSnapshotFromPlayer`, `hasNextTrack` and `collectorScope`.
- `AutomotivePlayerViewModel.restorePreviousSession` + `showRestoredSession` hold the full policy:
  read, idle re-check, send, `RESULT_SUCCESS` gate, snapshot write, like observer.
- `PlayerViewModel.restorePlaybackState` (`:app`, lines 419-452) holds a shorter version: read,
  send, direct `_uiState` write, like observer, and a `Restore Error` catch.
- Both ViewModels already fold `stateCollector.playbackState` into their own UI state.

## Decisions

### D-T10.1: The collector stays UI-agnostic

`applyRestored` and `restoreIfIdle` write the snapshot and nothing else. `PlayerMode.Mini` is
mobile's concept and stays in `PlayerViewModel`; the like observer stays in each ViewModel because
each owns its own repository call and job handle. The collector must not learn about either.

### D-T10.2: Mobile keeps its `Restore Error` path

`restore()` swallows its own exceptions, so mobile's catch is close to dead already — but removing
a user-visible error path is a product change, not a refactor. Keep the `try`/`catch` around the
new call, unchanged. If it should go, that is its own ticket with its own reasoning.

### D-T10.3: Mobile gains a correct duration on a restored session, and that is the one intended change

Mobile's restore never set `durationMs`, so a restored-and-paused session shows `0:00` as the
track's total until playback starts. The car fixed this in T3 by writing `restored.song.durationMs`.
Once the shared `applyRestored` writes the snapshot and mobile's existing
`observePlaybackSnapshot` folds `durationMs` through, mobile gets the same fix for free.

Call it out in the device pass rather than letting it look like a regression. Everything else about
mobile's restored UI must look identical.

### D-T10.4: The future is awaited with `suspendCancellableCoroutine`, not a new dependency

`sendRestoreState` returns `ListenableFuture<SessionResult>`; `restoreIfIdle` is `suspend` and runs
on `viewModelScope`. `kotlinx-coroutines-guava` would give `.await()` for one call site — not worth
a dependency. Bridge with `suspendCancellableCoroutine` over the existing
`addListener(…, MoreExecutors.directExecutor())`, reading the result inside the listener, where the
future is already complete so `.get()` does not block. (`directExecutor` runs the listener on
whichever thread completed the future, so the claim is "not blocking", not "not on main".)

**Resume with a value, never with an exception.** The car's `runCatching { … }.getOrNull()` today
turns a failed, cancelled or error-coded command into "do not apply". A bridge that rethrew an
`ExecutionException` would change that into a throw escaping `viewModelScope.launch` — a crash path
where there is currently a silent no-op. Map every non-`RESULT_SUCCESS` outcome to `null`, and keep
coroutine cancellation working via `invokeOnCancellation`.

## File plan

**Modify**

| File | Change |
|---|---|
| `core/playback/.../BasePlayerStateCollector.kt` | Add `applyRestored` and `restoreIfIdle` |
| `automotive/.../AutomotivePlayerViewModel.kt` | Call `restoreIfIdle`; delete both private methods |
| `app/.../player/PlayerViewModel.kt` | Call `restoreIfIdle`; delete the `_uiState` restore write |
| `docs/aaos-DESIGN.md` | D61 after implementation |
| `docs/tickets/T10-restore-policy-in-the-collector.md` | Status and outcome |
| `docs/tickets/T3-automotive-playback-restore.md` | Strike the two follow-ups this closes |

**Create**

| File | Responsibility |
|---|---|
| `core/playback/src/test/.../RestoredSnapshotTest.kt` | `applyRestored` field-by-field |

## Task 0: Baseline and branch

- [ ] Confirm `main` is clean and contains PR #44.
- [ ] Branch from `main`, run the focused gate once.

## Task 1: `applyRestored`, with tests

**Purpose:** one definition of what a restored session looks like, and the first restore code in
this project that a unit test can reach.

- [x] Add to `BasePlayerStateCollector`:

```kotlin
fun applyRestored(restored: RestoredPlayback)
```

- [x] It writes, via `updateSnapshot`, exactly the union of what the two copies write today:
      `currentSong`, `currentPositionMs`, `durationMs = restored.song.durationMs`,
      `isPlaying = false`, `repeatMode`, `queue`, `queueSize`, `currentQueueIndex = restored.index`,
      `hasPrevious = restored.index > 0`, and
      `hasNext = restored.index < restored.queue.lastIndex || restored.repeatMode == RepeatMode.All`.
- [x] It must **not** call `hasNextTrack()` — that asks the live controller, which is the timing
      trap D56 exists to avoid. The ticket explains why; do not "simplify" it back.
- [x] Add `RestoredSnapshotTest`. The collector is constructible in a unit test without a
      `MediaController`: pass a `SettableFuture.create()` that never completes and a `TestScope`,
      subclass it for `positionPollIntervalMs`, and never call `connectController()`.
- [x] Cases: every field lands; `hasNext` false at the queue's end with `RepeatMode.Off`; `hasNext`
      **true** at the queue's end with `RepeatMode.All`; `hasPrevious` false at index 0; a
      single-item queue; and `isPlaying` false even if the snapshot said true before.
- [x] Mutation-check the repeat-all term and the `hasPrevious` boundary. Dropping the repeat-all
      term failed `applyRestored_lastTrackWithRepeatAll_hasNext`; widening `index > 0` to
      `index >= 0` failed `applyRestored_firstTrack_hasNoPrevious` and the single-item case. 41
      tests otherwise green.

**Acceptance criteria:** the restored-snapshot contract is covered by name, with no
`MediaController` and no mocking library.

## Task 2: `restoreIfIdle`

**Purpose:** the sequence T3 established, owned once.

- [x] Add to `BasePlayerStateCollector`:

```kotlin
suspend fun restoreIfIdle(restore: suspend () -> RestoredPlayback?): RestoredPlayback?
```

- [x] Order: capture `controller` into a local (bail if null); run `restore()`; bail on null;
      **re-check `mediaItemCount > 0` on that same local after the suspend point** and bail if the
      player is no longer idle; `sendRestoreState`; await the result per D-T10.4; on
      `RESULT_SUCCESS` call `applyRestored` and return the value; otherwise return null.
- [x] Use the one captured controller throughout. Reading the `controller` property twice would let
      the re-check and the send disagree about which controller they are talking to.
- [x] Returning non-null must mean *the session is on screen* — read, idle, acknowledged, applied.
      Callers key their like-observer call off that.
- [x] Do not add a `Log` or an error channel here; failure is silent by D-T3.5 and mobile's own
      catch still covers the throwing case.

**Acceptance criteria:** the guard and the success gate exist exactly once in the repo.

**Note.** The failure code is `SessionError.ERROR_UNKNOWN`, not `SessionResult.ERROR_UNKNOWN` —
`SessionResult` carries only `RESULT_SUCCESS`. `PlaybackService.toSessionErrorCode()` already uses
`SessionError` for the same reason.

## Task 3: The car calls it

- [ ] Replace `restorePreviousSession` with a `viewModelScope.launch` that calls
      `stateCollector.restoreIfIdle { persistence.restore() }` and, on a non-null result, calls
      `observeCurrentSongLikeState(restored.song.mediaId)`.
- [ ] Delete `showRestoredSession` and the imports that go stale with it: `RepeatMode`,
      `RestoredPlayback`, `SessionResult`, `MoreExecutors` **and `sendRestoreState`** — the send
      moves into the collector, so neither ViewModel imports it any more. Check each against the
      rest of the file; `SessionCommand` and `Bundle` stay for the other commands.
- [ ] The comment explaining why the car's `hasNext` differs from mobile's goes with it. It only
      existed because the duplication did.

**Acceptance criteria:** `AutomotivePlayerViewModel` contains no snapshot arithmetic.

## Task 4: Mobile calls it

- [ ] In `restorePlaybackState`, replace the send and the `_uiState` block with the same
      `restoreIfIdle` call, keeping the `try`/`catch` (D-T10.2) and the like observer. Drop the
      `sendRestoreState` import here too.
- [ ] Keep `playerMode = PlayerMode.Mini` — set it only when the result is non-null, so a refused
      or failed restore no longer raises the mini player over an empty player. That is mobile's
      share of the T3 success gate.
- [ ] Confirm `observePlaybackSnapshot` carries the restored fields into `PlayerUiState`;
      `playerMode` is the only field it does not cover.

**Acceptance criteria:** mobile's restore is a launch, one call, and two lines of mobile-specific
reaction; its `hasNext` no longer exists as a second formula.

## Task 5: Gate

- [ ] Run the broader gate.
- [ ] `grep -rn "restored\." app/src/main automotive/src/main` returns only the two
      `observeCurrentSongLikeState(restored.song.mediaId)` calls — no snapshot arithmetic left in
      either ViewModel. (A `hasNext =` grep is the wrong check: mobile legitimately keeps
      `hasNext = snapshot.hasNext` in its snapshot folding, and previews set it too.)
- [ ] No new detekt baseline entries; confirm neither ViewModel needed a new suppression.

## Task 6: Device pass, both surfaces

- [ ] **Car**, `AAOS_AOSP_33_userdebug`: the T3 process-death protocol — play, note track/index/
      position/repeat/like, background, `am kill --user 10`, verify the PID changed, relaunch, and
      compare `dumpsys media_session` field by field against `docs/AAOS_T3_VERIFICATION.md`.
- [ ] **Mobile**, any phone or the phone AVD (one emulator at a time): play a queue past the first
      save, kill the app, relaunch, and confirm the mini player returns at the right song and
      position — and that the expanded player now shows the track's real duration rather than
      `0:00` (D-T10.3).
- [ ] Mobile also carries T3's D55 index fix untested. Delete a song that sits earlier in the saved
      queue, restore, and confirm the resumed track is still the saved one. This closes a debt T3
      left rather than adding scope.

**Acceptance criteria:** both surfaces restore identically, recorded with the same evidence style.

## Task 7: Docs

- [ ] `docs/aaos-DESIGN.md`: D61 — restore policy lives in the collector; what each surface keeps;
      why `applyRestored` computes from the value and not the controller.
- [ ] T10 ticket status and outcome.
- [ ] `docs/tickets/T3-automotive-playback-restore.md`: strike the two follow-ups this closes
      (mobile's discarded result, mobile's `hasNext`), leaving the sender duplication and the
      transport seam.

**Acceptance criteria:** no document still describes the two surfaces as diverging on restore.
