# T10 - Restore policy belongs in the shared collector, not in each ViewModel

- **Slice:** architecture - removes a duplication that T3 was forced to work around
- **Depends on:** T3 (merged, PR #41)
- **Status:** Proposed
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest :app:assembleDebug`
- **Design Reference:** `docs/aaos-DESIGN.md` D53–D59; `core/playback/.../BasePlayerStateCollector.kt`
- **Risk Tags:** shared code, two surfaces, unverifiable controller path
- **Affected Modules:** `:core:playback`, `:app`, `:automotive`

## Problem

`:core:playback` is shared by mobile and AAOS, and for live playback the sharing works exactly as
intended. `BasePlayerStateCollector` owns the `MediaController` and publishes one
`PlaybackSnapshot`; both ViewModels extend it and fold that snapshot into their own UI state — the
car as `playback = snapshot`, mobile field by field into `PlayerUiState`
(`PlayerViewModel.kt:132`). A change to how playback state is derived reaches both surfaces through
a flow they already observe. Nothing is duplicated and nothing has to be kept in sync by hand.

**Restore breaks that pattern by writing to the wrong place.** The car's `showRestoredSession`
writes the snapshot through `updateSnapshot`. Mobile's `restorePlaybackState` writes `_uiState`
directly, bypassing the collector entirely. So "what a restored session looks like" is implemented
twice, in two ViewModels, from the same `RestoredPlayback`.

Three consequences, all visible in T3:

1. **`hasNext` is computed twice and the copies disagree.** The car ORs in
   `repeatMode == RepeatMode.All`, because its `skipNext()` wraps on repeat-all; mobile uses a bare
   `index < queue.lastIndex` and dims a button that works. The car's line needs a comment
   explaining that the divergence is deliberate — a comment that only exists because the
   duplication does.
2. **The pre-send emptiness re-check exists on one surface.** Mobile can still replace live
   playback with a restored queue if a play arrives during its Firestore read. T3 proved that
   failure by mutation on the car; mobile has no guard.
3. **The success gate exists on one surface.** The car shows a restored track only after the
   service acknowledges the command; mobile paints it regardless, so a rejected command leaves
   mobile displaying a session the player never received.

The systemic cost is worse than any of the three. Every future playback-policy decision taken for
AAOS forces a choice between duplicating it into mobile untested, leaving mobile behind, or
widening the ticket to cover a surface it was not scoped for. T3 took the middle option three
times and paid for it in comments and follow-up items.

**What is not the problem:** sharing `:core:playback`. The D55 index fix — restore resuming the
saved song rather than the shifted index — was a genuine bug in shared code, and mobile should
have received it. The problem is *policy* duplicated per surface, not *behaviour* shared between
them.

## Proposed solution

Move restore policy down into the seam both surfaces already consume.

```kotlin
// BasePlayerStateCollector — it already owns the controller and the snapshot
fun applyRestored(restored: RestoredPlayback)

suspend fun restoreIfIdle(restore: suspend () -> RestoredPlayback?): RestoredPlayback?
```

- `applyRestored` writes the snapshot once: current song, position, duration, repeat mode, queue,
  index, and `hasNext` via the existing `hasNextTrack()`, which already handles repeat-all.
- `restoreIfIdle` owns the sequence T3 established: run the caller's read, re-check the player is
  still empty before sending, send through `sendRestoreState`, and apply the snapshot only on
  `RESULT_SUCCESS`. It returns the restored value so each surface can react.
- Each ViewModel keeps only what is genuinely its own. Mobile: `playerMode = PlayerMode.Mini`, and
  its restore-error policy if it keeps one. Car: `observeCurrentSongLikeState`.

Mobile's `restorePlaybackState` collapses to a launch, one call and one line. The car's
`restorePreviousSession` and `showRestoredSession` disappear. Two of T3's four follow-ups —
mobile's missing success gate and its `hasNext` term — stop being work items and become impossible.

Alongside it, one rule worth writing down: **a change under `:core:playback` is a change to both
surfaces.** The ticket either verifies both or names the surface it is leaving out and why. T3 did
the latter implicitly; making it explicit is free.

## Why this over the alternatives

- **Fix mobile by hand (today's backlog).** Three small commits, and it repairs exactly the three
  divergences that exist now. It prevents none of the next ones, because the duplication that
  produced them survives. Cheapest today, recurring forever.
- **Split the module so AAOS owns its own playback code.** The literal reading of "AAOS changes
  should not affect mobile", and it cannot be built: there is one `PlaybackService`, one
  `MediaLibrarySession`, one process. Two copies of the playback owner is not a separation of
  concerns, it is two products.
- **Restore inside `PlaybackService` (D53's rejected option).** The strongest separation — neither
  client owns policy, and the OEM media template would finally restore too. Still rejected for the
  same reasons as in T3: `:core:playback` has no service test harness, a service-side restore races
  any `CMD_SET_QUEUE` from a template play, and it rewrites mobile's proven path wholesale. It
  remains the right long-term answer for the template surface; it is not the cheap fix for this
  problem.
- **A parity test asserting both ViewModels produce the same snapshot.** Catches drift instead of
  removing it. A test whose job is to police duplication is an admission that the duplication
  should not exist.
- **This proposal.** It deletes the duplication rather than policing it, and it does so through a
  seam both surfaces already consume, so nothing new is invented: roughly fifteen lines move down,
  two private methods are deleted, and one comment explaining a deliberate divergence becomes
  unnecessary. It is also the only option that makes the two open mobile defects unreachable
  rather than scheduled.

## Out of scope

- Restoring for the OEM media template (D53 stands).
- Changing what mobile shows on a failed restore, beyond what falls out of the shared gate.
- The `MediaController` transport seam that would make the send path unit-testable.

## Acceptance criteria

- Given a `RestoredPlayback`, when either surface applies it, then the snapshot is written by one
  implementation and `hasNext` is computed once.
- Given playback starts while a restore read is in flight, when the read returns, then **neither**
  surface replaces what is playing.
- Given the restore command fails, when the result arrives, then neither surface shows a session
  the player did not receive.
- Given `:core:playback` unit tests run, then `applyRestored` is covered directly — it is plain
  Kotlin over a `MutableStateFlow` and needs no `MediaController`.
- Given the mobile app runs on a device, then restore still produces a mini player at the saved
  song and position.

## Notes

`restoreIfIdle` cannot be unit-tested end to end: it touches `MediaController`, which is
`@DoNotMock` with a package-private constructor. `applyRestored` can be, and it holds the logic
worth testing. The send path stays device-verified, as in T3.

Mobile also still carries the D55 index fix without a device pass. That predates this ticket and is
not fixed by it, but T10's device step is the natural moment to close it.
