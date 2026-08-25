# T3 - Restore playback state in the automotive custom launcher

- **Slice:** player lifecycle - cross-cutting, not an A5 overlay patch
- **Depends on:** A5 verification evidence; D29 ViewModel boundary decision
- **Status:** Specced — plan at `docs/superpowers/plans/2026-08-25-aaos-t3-automotive-playback-restore.md`
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest`
- **Design Reference:** `docs/AAOS_A5_VERIFICATION.md` process-death observation; `docs/aaos-DESIGN.md` D29
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
