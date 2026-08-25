# T3 - Restore playback state in the automotive custom launcher

- **Slice:** player lifecycle - cross-cutting, not an A5 overlay patch
- **Depends on:** A5 verification evidence; D29 ViewModel boundary decision
- **Status:** Implemented and device-verified; two checks unrun — see Outcome
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest`
- **Design Reference:** `docs/AAOS_A5_VERIFICATION.md` process-death observation; `docs/aaos-DESIGN.md` D29
- **Plan:** `docs/superpowers/plans/2026-08-25-aaos-t3-automotive-playback-restore.md`
- **Verification:** `docs/AAOS_T3_VERIFICATION.md`
- **Risk Tags:** lifecycle, playback state, ViewModel size, process death
- **Affected Modules:** `:automotive`; likely `:core:playback`

## Problem

A5 verified that the full player relaunches without a crash after process death, but the automotive
custom launcher comes back with an empty player: no current song, `0:00 / 0:00`, and an empty media
queue. The existing restore path is mobile-only: `PlaybackStatePersistence` is consumed by
`app/src/main/java/com/example/nyasaplayer/player/PlayerViewModel.kt`, while
`AutomotivePlayerViewModel` has no restore wiring.

This is out of A5 scope. A5 completed the full-player and queue overlay contract. Restoring playback
state requires player lifecycle work and likely a new player API surface.

## Scope

- Define how the automotive player restores the previous queue, current item, position and repeat
  mode after process death.
- Reuse `PlaybackStatePersistence` and the existing playback command model where possible.
- Fix the index bug found while speccing this: `restore()` drops unresolvable ids with `mapNotNull`
  but still indexes by the saved `queueIndex`, so a catalogue deletion earlier in the queue resumes
  the wrong song. Shared code, so mobile gets the fix too (plan D-T3.8).
- Split or otherwise contain `AutomotivePlayerViewModel` before adding new public player APIs, per
  D29, rather than expanding the current suppressed ViewModel by default.
- Keep the OEM media-template path working; do not regress `PlaybackService` restore behavior.
- Record the restore contract in the AAOS architecture/design docs once implemented.

## Out Of Scope

- Reopening A5's queue display-window logic.
- Changing the A5 buffering indicator or error overlay.
- Adding queue reorder.
- Redesigning mobile playback restore.

## Acceptance Criteria

- Given playback state was persisted before process death, when the `oem` custom launcher is
  relaunched, then the full player reflects the restored current song, queue, position and repeat
  mode.
- Given the persisted queue is missing, corrupt or empty, when restore runs, then the automotive
  player fails gracefully without a crash and without a misleading active-player state.
- Given restore adds a new player operation, when detekt runs, then `AutomotivePlayerViewModel` is
  not widened by another suppression; the split decision from D29 is honored or explicitly revised.
- Given unit tests run, then restore success, missing-state and corrupt-state cases are covered
  without requiring an emulator. **Amended 2026-08-25:** these land in `:core:playback`
  (`PlaybackStatePersistenceTest`), not `:automotive`. `MediaController` is `@DoNotMock` with a
  package-private constructor, so the car ViewModel's controller path cannot be driven in a unit
  test at all; asserting the restore cases against an automotive fake would test the fake, not the
  branching, which lives in `PlaybackStatePersistence`. See the plan's carve-out.
- Given manual AAOS process-death verification runs, then the result is recorded with the same PID
  and `dumpsys media_session` evidence style used in `docs/AAOS_A5_VERIFICATION.md`.

## Notes

The A5 verification carve-out was "empty player on return", not a crash. That makes this a follow-up
quality and lifecycle ticket, not an A5 acceptance blocker.

## Outcome

Implemented across six tasks. Design records D53–D59 in `docs/aaos-DESIGN.md`.

The defect was one missing `else`. Every piece restore needs already existed and worked on mobile:
`PlaybackStatePersistence.restore()`, `CMD_RESTORE_STATE`, `handleRestoreState()`, and a service
that had been saving the car's state all along. `AutomotivePlayerViewModel.onControllerConnected`
simply had no branch for an empty player. It has one now, through the same command mobile sends —
one sender, in `PlaybackCommands.kt`, mobile's private copy deleted.

Two things the car does that mobile does not, both from the external review. It re-checks the
player is still empty after the Firestore read returns, because a template play arriving during
that round trip would otherwise be replaced and paused (D56). And it shows the restored track only
once the service acknowledges the command, so a rejected restore leaves an empty player rather
than a session the player never received.

Making restore testable found a real bug on the way. `PlaybackStatePersistence` identified the
user through `FirebaseUser`, which no fake can construct, which is why `restore()` had never had a
test; switching to `currentUserId` (D54) unlocked ten. The first of them found that the current
track was chosen by the saved index while the queue silently drops ids that no longer resolve — so
one catalogue deletion earlier in the queue resumed a *different* song at the previous song's
position (D55). Shared code, so mobile carries that fix too — not device-verified there.

Device run: PID 2485 → 2721, track, index, position, repeat mode, like state, paused and queue
size all identical across the kill. Full table in `docs/AAOS_T3_VERIFICATION.md`.

## Not verified

- **The D56 re-check.** Four attempts could not reach the state worth testing: the window between
  the read returning and the send is sub-second, while the emulator needs 15–30s after a relaunch
  before Home has anything to tap. Network throttling did not widen it — the document comes from
  Firestore's local cache. Two instrumented builds carrying a `delay()` did hold the window open
  and showed restore still lands correctly behind one, but every tap into that window was
  swallowed. Both were reverted before the final run. The guard's presence is a code fact, not a
  device observation.
- **The missing-state case on device.** Reaching it means deleting
  `users/{uid}/playbackState/current`, and both routes to that were refused by the session's
  permission classifier. Left unrun rather than worked around. `PlaybackStatePersistenceTest`
  covers the branch; what is missing is the device-level claim that a `null` restore leaves an
  empty player with no crash and no overlay.
- **The `:automotive` unit tests the original fourth acceptance criterion asked for.** Amended
  above: `MediaController` is `@DoNotMock` with a package-private constructor, so the ViewModel's
  controller path cannot be driven in a unit test at all. Closing that means a transport seam over
  the controller operations both ViewModels use — its own ticket, and it would let mobile's player
  be tested too.

## Follow-ups this slice deliberately did not take

- **Mobile discards the restore command's result.** `PlayerViewModel.restorePlaybackState()` sends
  `sendRestoreState` and shows the restored track without waiting for the `SessionResult`, so a
  rejected command leaves mobile displaying a session the player never received — the failure the
  car now guards against. Pre-existing, and mobile restore is out of T3's scope; the shared
  sender's KDoc names which caller checks and which does not, so the divergence is visible at the
  call site rather than implied.
- **Mobile's restore leaves `hasNext` false at the end of a repeat-all queue**, because its
  snapshot omits the `repeatMode == RepeatMode.All` term the car's has. Self-corrects on the first
  `Player.Listener` callback, so the visible window is short; both reviewers flagged the
  divergence, and the car's line carries a comment saying it is deliberate.
- **A transport seam over `MediaController`**, which would let both ViewModels' player paths be
  unit-tested. Named in the plan's carve-out.
