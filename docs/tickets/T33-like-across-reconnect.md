# T33 - A like made while Firestore was unreachable may not survive the reconnect

- **Slice:** likes, offline behaviour — mobile first, the car shares the path
- **Depends on:** nothing
- **Status:** Filed 2026-09-19 from an observation, **not reproduced**. No fix, no test.
- **Verification Command:** none yet — the first job is a reproduction
- **Design Reference:** `PlayerViewModel.toggleLike()`; `UserRepository` (`users/{uid}/likedSongs`)
- **Risk Tags:** offline, silent data loss, user-visible
- **Affected Modules:** `:app` (the ViewModel and its Snackbar), `:core:data` (`UserRepository`)

## Problem

During T13's offline pass (`docs/T13_VERIFICATION.md`, "Observation, not reproduced") the heart on
"Vanity Remix" showed **filled** across the offline window, and **unfilled** after the network came
back — with the session's custom action back at `Like`. The like had been tapped minutes earlier,
while Firestore was logging `Could not reach Cloud Firestore backend`.

Two readings, and the verification note could not tell them apart:

| Reading | What it means |
|---|---|
| Working as specified | `toggleLike()` is meant to roll back optimistically and show a Snackbar on failure. If the write failed and the Snackbar was missed, this is exactly what it should look like. |
| A real defect | The optimistic state survived long enough to look committed, and the rollback landed only on reconnect — so the driver believes a like was saved that never was. |

No deliberate attempt was made to reproduce it, and nothing was recorded from the Snackbar host at
the time. That is why this is a ticket and not a fix: the first deliverable is knowing which of the
two it is.

## Scope

- Reproduce deliberately: sign in, go offline at the Firestore level (not just airplane mode — the
  offline cache changes the failure), tap like, wait past the Firestore backend timeout, reconnect.
- Record what the Snackbar host did. If no Snackbar appeared on a failed write, that is the defect,
  independent of the like itself.
- If the write is being queued by Firestore's offline cache and then discarded, say where.

## Out Of Scope

- Redesigning like into an offline-durable write with its own outbox. If the optimistic-rollback
  contract turns out to be working and merely invisible, the fix is the Snackbar, not a queue.
- The car's like path, until mobile's is understood — both call the same repository.

## Acceptance Criteria

- Given Firestore is unreachable, when the driver likes a song and the write fails, then the UI
  either keeps the like (because it was durably queued) or reverts it **and says so**, and never
  shows a filled heart for a like that was silently dropped.
- The reproduction, or the failure to reproduce after a deliberate attempt, is written down.

## Notes

`toggleLike` already has a Snackbar-on-failure path and an optimistic rollback; this ticket does not
assume either is broken. It assumes only that nobody has watched them run under a real Firestore
outage.
