# AAOS T3 - Automotive Playback Restore Implementation Plan

> **For agentic workers:** The restore machinery already exists and works on mobile. This slice
> connects it to the car, fixes one latent bug in it, and makes it testable. Do not redesign mobile
> restore, do not move restore into `PlaybackService`, and do not add queue reorder or touch A5's
> display-window logic.

**Goal:** After process death, relaunching the `oem` custom launcher brings back the queue, current
song, position and repeat mode the driver left, instead of an empty player showing `0:00 / 0:00`.

**Ticket:** `docs/tickets/T3-automotive-playback-restore.md`

**Verification command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest`

**Broader gate:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest
:core:data:testDebugUnitTest detekt :automotive:lintOemDebug :automotive:assembleOemDebug
:automotive:assemblePlaystoreDebug :app:assembleDebug`

**External review:** `codex exec`, 2026-08-25 — nine findings, seven applied below (two blockers:
the pre-send emptiness re-check in D-T3.7, and the corrected `MediaController` rationale in the
carve-out). The two declined are recorded at the end.

## Current baseline

Start from `main` after T2 (PR #40). What already exists, verified in the source before this plan
was written:

- `PlaybackStatePersistence.restore(): RestoredPlayback?` — a **suspend** function that reads the
  saved state, resolves song ids through `SongRepository`, coerces the index, falls back to
  `RepeatMode.Off` on an unparseable mode, and returns `null` on any failure.
- `PlaybackService` **already saves the car's state**: `saveState()` every 30s
  (`PersistIntervalMs`) and when playback pauses, `saveFinalState()` in `onDestroy()`.
  Saving is not the missing piece — but see D-T3.9 for how fresh the saved position actually is
  when the process is killed.
- `PlaybackCommands.CMD_RESTORE_STATE` and `PlaybackService.handleRestoreState()` — restores the
  queue, applies the repeat mode, seeks to `(index, positionMs)` and sets `playWhenReady = false`.
- `PlayerViewModel` (mobile) calls `persistence.restore()` from `onControllerConnected` when the
  player is empty, then sends the command.
- `AutomotivePlayerViewModel.onControllerConnected` has **only** the non-empty branch:
  `if (controller.isPlaying || controller.mediaItemCount > 0) syncSnapshotFromPlayer(controller)`.
  There is no `else`. That single missing branch is the whole user-visible defect.

So T3 is not "build restore". It is: add the missing branch, share the one piece of mobile-private
code it needs, fix the index bug in D-T3.8, and pay off the reason this path has never had a test.

## Decisions

### D-T3.1: Restore stays in the client, not in `PlaybackService`

The tempting root fix is restoring inside the service on first connect, which would cover mobile,
the car launcher and the OEM template at once. Rejected for this slice:

- It changes mobile's restore path, which the ticket puts out of scope and which has device
  evidence behind it today.
- There is no service test harness in the repo — `:core:playback`'s only test is
  `MediaBrowseTreeTest`, which tests a callback object, not a started service.
- A service-side restore races any `CMD_SET_QUEUE` arriving from a template play while the
  Firestore read is in flight, and would need its own "is the player still empty" guard.

**Consequence to record, not fix:** a driver who uses *only* the OEM media template still gets an
empty session after process death. The template surface has no client that restores. If that
becomes a requirement (some OEM media-resumption checklists ask for it), it is a service-side
ticket, and D-T3.1 is the decision it should argue against.

### D-T3.2: `PlaybackStatePersistence` reads `currentUserId`, not `currentUser?.uid`

`private val userId get() = authRepository.currentUser?.uid` is why restore has never had a unit
test: `FirebaseUser` cannot be constructed, so no fake can make the persistence believe anyone is
signed in. `AuthRepository.currentUserId` was added for exactly this reason and its implementation
is `firebaseAuth.currentUser?.uid` — the same value, one line apart.

Change the property to `authRepository.currentUserId`. This is a behaviour-preserving edit that
unlocks Task 1's tests; make it first, not as a drive-by inside the car wiring.

### D-T3.3: One restore-command sender, in `:core:playback`

`PlayerViewModel.sendRestoreCommand()` is private and builds the `CMD_RESTORE_STATE` bundle by
hand. The car needs the identical bundle. Move it next to `RestoredPlayback`:

```kotlin
// core/playback/.../PlaybackStatePersistence.kt (or a small file beside it)
fun MediaController.sendRestoreState(restored: RestoredPlayback): ListenableFuture<SessionResult>
```

Return the future rather than swallowing it — D-T3.7 needs it. Delete the mobile copy and call the
shared one; mobile may ignore the returned future. Two hand-built copies of the same bundle keys is
how the A6 skeleton drift (T7) started; one sender is cheaper than a second one.

### D-T3.4: Restore does not auto-play, and is not gated on driving state

`handleRestoreState` already ends with `playWhenReady = false`, so restore produces a paused mini
player and no audio. Nothing appears that the driver must interact with, so restore runs the same
whether the vehicle is parked or moving. Gating it on `isDistractionOptimized` would mean the
driver who parks, drives off and comes back to a killed app gets a *worse* result while moving —
the opposite of what the restriction is for.

### D-T3.5: A failed restore is silent on the car

`restore()` already returns `null` for every failure mode — no user, no saved document, blank song
id, unresolvable ids, thrown exception. The car does nothing with `null`: the player stays empty,
which is exactly today's behaviour and no worse. No `CarErrorOverlay`.

Mobile shows a "Restore Error" `PlayerError`; that branch is near-dead because `restore()` swallows
its own exceptions, and copying it to the car would put a dismissible dialog in front of a driver
for something they cannot act on. Do not copy it.

### D-T3.6: No new public method on `AutomotivePlayerViewModel`, so D29 is honoured, not revised

D29 says the next slice that needs a **new public method** on this ViewModel must split it rather
than suppress again. Restore is triggered from inside the existing `onControllerConnected` override
and adds only private members, so the split trigger is not met and the existing class-level
`@Suppress("TooManyFunctions")` is not widened. The constructor grows from five parameters to six
against `constructorThreshold: 10`.

If implementation finds it needs a public `restorePlaybackState()` — for a retry affordance, say —
stop and split first. That is D29's rule, and this plan does not get to relax it.

### D-T3.7: The snapshot is written from `RestoredPlayback`, but only after the command succeeds

Three rules, in order:

1. **Re-check emptiness immediately before sending.** `restore()` is a suspend Firestore read; the
   player can be empty when `onControllerConnected` fires and playing by the time the read returns
   — the driver taps a song in the template, or the OEM template resumes something. Sending
   `CMD_RESTORE_STATE` then would silently replace what is playing and pause it, because
   `handleRestoreState` applies the queue unconditionally. So: `if (controller.mediaItemCount > 0)
   return` after the await, before the send. This is the one hole that turns a missing feature into
   a regression.
2. **Write the snapshot in the command future's callback, on `RESULT_SUCCESS` only.**
   `onCustomCommand` can return `ERROR_NOT_SUPPORTED` or an error code from its catch block, and
   an optimistic write ahead of that would leave the car showing a track the player does not have,
   with no listener callback guaranteed to correct it.
3. **What to write:** `currentSong`, `durationMs` (from `restored.song.durationMs` — the position
   poller only runs while playing, so nothing else fills the scrubber's total), `currentPositionMs`,
   `isPlaying = false`, `queue`, `queueSize`, `currentQueueIndex`, `hasPrevious = index > 0`,
   `hasNext = index < queue.lastIndex || repeatMode == RepeatMode.All`, and `repeatMode`.

That `hasNext` term matters: `BasePlayerStateCollector.hasNextTrack` and the car's `skipNext()`
both treat repeat-all as having a next track, so mobile's plain `index < lastIndex` would dim a
button that works. Do not copy mobile's line verbatim.

Do **not** wait on the controller's own state and then call `syncSnapshotFromPlayer`: the
`SessionResult` future completes on the session side, which does not prove the controller has
received the state update. The listener callbacks correct the snapshot moments later anyway.

### D-T3.8: Restore resumes the saved **song**, not the saved index

`restore()` builds `orderedQueue` with `mapNotNull`, which silently drops ids that no longer
resolve, and then reads `orderedQueue[saved.queueIndex.coerceIn(...)]`. If any song before the
current one was deleted from the catalogue, every later index shifts and the driver resumes on the
wrong track — at the saved position of a different song.

`saved.currentSongId` is stored precisely so this is recoverable. Resolve the index by locating
that id in the ordered queue first, and fall back to the coerced saved index only when the id is
gone. This is a bug in existing shared code, so mobile gets the fix too; it is in scope because T3
is the ticket that makes restore correct on a second surface.

### D-T3.9: The restored position is accurate to the save interval, not to the moment of death

`am kill` — and a real low-memory kill — does not run `onDestroy()`, so `saveFinalState()` does not
fire. The freshest state is whatever the 30s `PersistIntervalMs` loop or the pause-save left. The
driver can therefore return up to 30 seconds behind where they were.

This is the existing contract on both surfaces and T3 does not change it. Record it rather than
paper over it, and do not add a save-on-every-transition loop inside this slice — that is a
service-side change with its own Firestore write-rate question.

## File plan

**Create**

| File | Responsibility |
|---|---|
| `core/playback/src/test/.../PlaybackStatePersistenceTest.kt` | The restore contract: success, missing, corrupt |

**Modify**

| File | Change |
|---|---|
| `core/playback/.../PlaybackStatePersistence.kt` | `currentUserId` (D-T3.2); song-first index (D-T3.8); add `MediaController.sendRestoreState` (D-T3.3) |
| `app/.../player/PlayerViewModel.kt` | Delete private `sendRestoreCommand`, call the shared sender |
| `automotive/.../viewmodel/AutomotivePlayerViewModel.kt` | Inject persistence; restore when the player is empty |
| `core/playback/src/test/.../MediaBrowseTreeTest.kt` | Make its fakes reusable and give them settable state |
| `docs/aaos-DESIGN.md` | D53+ after implementation |
| `docs/tickets/T3-automotive-playback-restore.md` | Status, amended test AC, outcome |
| `docs/AAOS_A5_VERIFICATION.md` | Point its "empty player on return" carve-out at T3's result |

## Task 0: Baseline and branch

- [ ] Confirm `main` is clean and contains T2 (PR #40 merged).
- [ ] Branch `ek/aaos-t3-automotive-playback-restore`.
- [ ] Run the focused gate once to record a green starting point.

**Acceptance criteria:** the branch starts from post-T2 `main` and the gate passes before any edit.

## Task 1: Make restore testable, then test it

**Purpose:** The branchy code T3 depends on has never had a test, for a fixable reason.

- [x] Change `PlaybackStatePersistence.userId` to `authRepository.currentUserId` (D-T3.2).
- [x] In `MediaBrowseTreeTest.kt`, drop `private` from `TestUserRepository` / `TestSongRepository` /
      `TestAuthRepository` (same package and source set), give `TestUserRepository` a settable
      `playbackState` (it hardcodes `null` today), and give `TestAuthRepository` a way to report a
      user id without a `FirebaseUser`. `TestSongRepository`'s song list is already settable.
- [x] Add `PlaybackStatePersistenceTest` covering `restore()`:
      - no signed-in user → `null`
      - no saved document → `null`
      - blank `currentSongId` → `null`
      - none of the saved ids resolve → `null`
      - some ids resolve → queue keeps the **saved** order, not the repository's
      - **a song before the current one no longer resolves → the returned `song` is still the one
        `currentSongId` names** (D-T3.8)
      - `currentSongId` itself is gone → falls back to the coerced saved index without crashing
      - `queueIndex` past the end → coerced to `lastIndex`
      - unparseable `repeatMode` string → `RepeatMode.Off`
      - happy path → queue, index, song, position and mode all survive
- [x] Mutation-check: delete the `coerceIn`, then the song-first lookup, then the saved-order
      `mapNotNull`, and confirm a differently-named test fails each time. Each did:
      `queueIndexPastEnd_coercesToLastIndex`, `songDeletedEarlierInQueue_stillResumesTheSavedSong`
      and `queueKeepsSavedOrder_notRepositoryOrder`, one failure per mutation, 34 tests otherwise
      green.

**Note on the D-T3.8 test.** The first version of it deleted a song from a queue whose current
track was last, so `coerceIn` landed on the right song by accident and the test passed against the
unfixed code. It only goes red once the saved index is *in range and wrong* — five ids saved, one
deleted before the current one. A shift bug needs a queue long enough to shift.

**Acceptance criteria:** `:core:playback:testDebugUnitTest` covers every `null` return of
`restore()` by name, plus the D-T3.8 shift, with no mocking library added.

## Task 2: One restore sender

- [ ] Add `fun MediaController.sendRestoreState(restored: RestoredPlayback)` in `:core:playback`,
      building the same bundle mobile builds today (`KEY_SONGS`, `KEY_START_INDEX`,
      `KEY_POSITION_MS`, `KEY_REPEAT_MODE`) and returning the command future.
- [ ] Replace `PlayerViewModel.sendRestoreCommand` with a call to it and delete the private copy.
- [ ] `./gradlew :app:assembleDebug` to prove mobile still builds.

**Acceptance criteria:** exactly one place in the repo builds a `CMD_RESTORE_STATE` bundle.

## Task 3: Restore in the car

- [ ] Inject `PlaybackStatePersistence` into `AutomotivePlayerViewModel`.
- [ ] Add the missing `else` branch in `onControllerConnected`: when the player is neither playing
      nor holding items, launch a restore on `viewModelScope`.
- [ ] After the suspend `restore()` returns and before sending: bail out if the controller now
      holds items (D-T3.7 rule 1).
- [ ] Send through the shared sender; on `RESULT_SUCCESS` write the snapshot per D-T3.7 rule 3 and
      call the existing `observeCurrentSongLikeState(restored.song.mediaId)` so the heart is right
      on return. On a non-success result, leave the player empty.
- [ ] On `null` from `restore()`: do nothing (D-T3.5).
- [ ] Do not add a public method (D-T3.6).
- [ ] Confirm the restore cannot double-fire: a reconnect after the first restore sees a non-empty
      player and takes the sync branch.

**Acceptance criteria:** the car's `onControllerConnected` has both branches mobile has, it cannot
overwrite playback that started while the read was in flight, and the class-level suppression is
unchanged.

## Task 4: Static gate

- [ ] `./gradlew detekt :automotive:lintOemDebug` — no new baseline entries, no new suppression.
- [ ] Confirm `AutomotivePlayerViewModel`'s constructor is within `constructorThreshold: 10`.

**Acceptance criteria:** the static gate is green without editing `detekt-baseline.xml`.

## Task 5: Device verification

Same evidence style as `docs/AAOS_A5_VERIFICATION.md` — PID before and after, and
`dumpsys media_session`.

- [ ] Signed in, play a queue, note the track, queue size and position; wait past one 30s save
      (D-T3.9 — without that wait the run measures the save interval, not restore).
- [ ] Background the app, `am kill --user 10`, record PID before → after.
- [ ] Relaunch: full player shows the track, the queue size matches, position is within the save
      interval of where it was, repeat mode survived, and playback is **paused**, not started.
- [ ] `dumpsys media_session` reports a non-zero queue size.
- [ ] Missing-state case: delete the user's `playbackState` document, kill, relaunch — empty
      player, no crash, no error overlay.
- [ ] Race case, best effort: kill, relaunch, and immediately start a song from the OEM template.
      What must not happen is the tapped song being replaced by the restored queue.
- [ ] Note whether the OEM template's Now Playing also shows the restored track once the launcher
      has restored (it shares the session, so it should) — an observation, not a claim that the
      template restores on its own (D-T3.1).

**Acceptance criteria:** the run is recorded with PIDs and `dumpsys` output, including the
missing-state case and whatever the race attempt showed.

## Task 6: Docs

- [ ] Append D53+ to `docs/aaos-DESIGN.md` for D-T3.1, D-T3.2, D-T3.5, D-T3.6, D-T3.8 and D-T3.9 —
      the ones a future reader could otherwise undo or re-litigate.
- [ ] Update the ticket status and write its Outcome, including anything not verified.
- [ ] Amend `docs/AAOS_A5_VERIFICATION.md`'s "Process death returns an empty full player" note to
      point at T3 rather than reading as current.

**Acceptance criteria:** no document still describes the car as having no restore wiring.

## Known carve-out: the ViewModel-to-`MediaController` boundary stays device-verified

`androidx.media3.session.MediaController` is annotated `@DoNotMock` and its constructor is
package-private specifically to prevent subclassing outside `androidx.media3.session`. Building a
real one means standing up a live `MediaSession` for the test to connect to; no test in this repo
does that, and `grep` finds zero test references to the type. So Task 3's wiring cannot be
unit-tested as things stand: the restore call reaches `stateCollector.controller`, which is `null`
in any test that hands the ViewModel an unresolved future.

**This deviates from the ticket's fourth acceptance criterion**, which asks for the restore cases
in `:automotive` unit tests. The cases are covered in Task 1 instead, in `:core:playback`, where
the branching actually lives — asserting them again against an automotive fake would test the fake.
The ticket is amended to say so rather than left reading as satisfied.

Closing the gap properly means a transport seam — a small interface over the controller operations
both ViewModels use — which would let mobile's player be tested too. It is its own ticket. T3 must
not half-build it.

## Declined review findings

- **Local fakes instead of reusing `MediaBrowseTreeTest`'s.** Writing three more repository fakes
  costs ~60 lines of overrides to avoid two additive edits in a neighbouring test file. Reuse wins;
  if the two tests ever pull the fakes in different directions, split them then.
- **"The mutation-check steps are plan noise."** They are the house habit that has caught real
  defects on this project (A4's R0–R7 pass). Kept.
