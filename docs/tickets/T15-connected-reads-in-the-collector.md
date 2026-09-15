# T15 - The collector's reads ask the field; its commands ask the predicate

- **Slice:** correctness of published state
- **Depends on:** T11 (merged, PR #47), T14 (merged, PR #50)
- **Status:** Implemented — merged in PR #53 (the original approach was withdrawn, see Notes)
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest`
- **Design Reference:** `docs/aaos-DESIGN.md` D63
- **Risk Tags:** lifecycle, state accuracy
- **Affected Modules:** `:core:playback`

## Problem

T11 settled what a usable controller is: non-null **and** connected (D63). `PlayerTransport` applies
that predicate to every command through `connectedOrNull()`. Two of `BasePlayerStateCollector`'s
reads still open with `controller ?: return`, which is the null check T11 established is not an
availability check.

A disconnected `MediaController` does not throw. It logs and answers with defaults, and the two
reads that ask it turn those defaults into statements about the player:

- `hasNextTrack(repeatMode)` — `hasNextMediaItem()` answers `false`, but the expression is
  `false || repeatMode == RepeatMode.All`, so on a dead controller in repeat-all it returns **true**
  and the snapshot publishes `hasNext = true`. With no controller at all it returns `false`. Same
  absent player, opposite answers.
- `restoreIfIdle(restore)` — `mediaItemCount` answers `0`, which is exactly the shape of "the player
  is empty, restore onto it". The guard meant to protect a *playing* player waves a dead one
  through, so a Firestore read runs and a restore command is sent to a session that is gone. The
  result is a correct silent null, arrived at by asking a controller that could not answer.

Neither is the 2026-08-26 bug over again — commands are guarded, so nothing the driver taps lies.
It is the same class: a field that outlives its connection, read as if it were the connection.

## Scope

- `hasNextTrack()` and `restoreIfIdle()` adopt the T11 predicate, so a disconnected controller reads
  the same as no controller.
- `connectedOrNull()` widens from `private` to `internal` — it is the availability predicate for the
  module (D63), not a `PlayerTransport` detail.
- One test per read, on the existing `ReconnectingCollectorTest` harness: `loseTheController()`
  already produces a non-null disconnected controller.
- D63 gains a line: the predicate covers reads, not just commands.

## Out Of Scope

- Nulling the field on disconnect. See Notes.
- Reconnection (T14, merged) — a read must not trigger one. Queries stay silent (D63), and
  `connectedOrNull()` does not report, so this stays true by construction.
- Publishing availability into `PlaybackSnapshot` so the UI can dim dead controls — that is T16, and
  it is the ticket that needs the interesting answer. (T16 has since closed; see its Outcome.)

## Acceptance Criteria

- Given a disconnected controller and repeat-all, then `hasNextTrack()` returns `false`, matching the
  no-controller answer.
- Given a disconnected controller, then `restoreIfIdle()` returns null **without** running the
  restore read or sending a command.
- Given a read against a disconnected controller, then no reconnect is started and nothing is
  reported to the surface.
- Given normal teardown, then no playback error is raised.

## Notes

**The filed approach is withdrawn.** It asked for a `MediaController.Listener` that nulls
`controller` in `onDisconnected`. T14 has since moved ownership of the future to
`ControllerConnection`, which releases when the last consumer leaves and rebuilds on demand; a
collector that also nulls the field is a second owner racing the first, and the field is re-assigned
by `attach()` on the reconnect anyway. It would add a listener, a lifecycle and a race to reach a
state the predicate already describes for free.

The window the original ticket flagged is real and stays: a controller can die between the check and
the use. It is not closeable by any amount of clearing — `PlayerTransport` says as much in its KDoc —
and both reads degrade to a silent null or `false` when it happens, which is the correct answer.

**Why only these two reads.** Verified against the Media3 1.5.1 sources: `isPlaying()`,
`hasNextMediaItem()`, `getMediaItemCount()` and friends already answer `isConnected() && impl.xxx()`
themselves, so a disconnected controller hands back a safe default rather than throwing. The position
poller and the `Player.Listener` callbacks use that default as-is and so fail closed — and
`release()` synthesizes no `Player.Listener` callback, so those cannot fire post-disconnect at all.
These two reads are the only places that build a claim on top of the default: an `|| repeatMode ==
All` that turns false into true, and an idle check that reads 0 as "empty, restore onto it".

**Why it is still worth doing** with commands already guarded: the reads feed `PlaybackSnapshot`, and
mobile's `MiniPlayer` and `ExpandedPlayer` already enable skip-next from `hasNext`. A `hasNext` that is
true because nothing was listening is a button that looks live over a player that is not.
