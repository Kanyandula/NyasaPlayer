# T12 - The other two command senders are still written twice

- **Slice:** cleanup - the tail of T3's shared-sender pattern
- **Depends on:** T3 (merged, PR #41)
- **Status:** Implemented
- **Verification Command:** `./gradlew :automotive:assembleOemDebug :app:assembleDebug detekt`
- **Design Reference:** `core/playback/.../PlaybackCommands.kt`, `sendRestoreState`
- **Risk Tags:** duplication, wire format
- **Affected Modules:** `:core:playback`, `:app`, `:automotive`

## Problem

`sendSetQueueCommand` and `sendShufflePlayCommand` are byte-identical in `PlayerViewModel` and
`AutomotivePlayerViewModel` — same keys, same `SessionCommand`, same `controller ?: return`. Two
hand-built copies of a wire format, which is exactly what T3 removed for `CMD_RESTORE_STATE` when
it moved the bundle-building into `MediaController.sendRestoreState`.

They were left alone then because neither command was on the restore path. There is no reason left.

## Scope

- Add `sendSetQueue(songs, startIndex)` and `sendShufflePlay(songs)` beside `sendRestoreState` in
  `PlaybackCommands.kt`, as `MediaController` extensions.
- Delete both private copies from each ViewModel.

## Out Of Scope

- Changing what either command does, or when a surface sends it.
- The optimistic snapshot writes each ViewModel does around these calls — those are per-surface
  reactions, and unlike restore they differ for real (mobile resolves local download URIs first,
  and opens the expanded player; the car opens nothing).

## Acceptance Criteria

- Given `grep -rn "CMD_SET_QUEUE\|CMD_SHUFFLE_PLAY" --include=*.kt`, then only
  `PlaybackCommands.kt` and `PlaybackService.kt` mention them.
- Given both apps build and detekt runs, then no new baseline entries and no behaviour change.

## Notes

Roughly 30 lines removed. The senders return `ListenableFuture<SessionResult>` like
`sendRestoreState` does, though neither caller has a reason to read it today — T11 may.

## Outcome

`sendSetQueue` and `sendShufflePlay` sit beside `sendRestoreState` in `PlaybackCommands.kt`, next to
the keys they use. 52 deletions against 41 insertions; `CMD_SET_QUEUE` and `CMD_SHUFFLE_PLAY` are now
named only there and in `PlaybackService.kt`.

`toBundle` went stale in both ViewModels — it was only there for the two bundles that moved — and
`Song` had to be imported into `PlaybackCommands.kt` for the new signatures. No behaviour change:
each call site kept the silent no-controller semantics the private copies had, which is T13's
problem, not this one's.
