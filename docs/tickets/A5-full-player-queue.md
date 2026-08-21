# AAOS A5 — Full Player and Queue rebuilt against the screen contract

- **Slice:** A5 (screens 12 and 13)
- **Depends on:** A2 (merged)
- **Status:** Merged — PR #24. Device-verified with carve-outs; retryable-error and process-death restore follow-ups recorded
- **Spec:** `docs/superpowers/specs/2026-08-20-aaos-full-player-queue-design.md`
- **Plan:** `docs/superpowers/plans/2026-08-20-aaos-a5-full-player-queue.md`
- **Verification Record:** `docs/AAOS_A5_VERIFICATION.md`
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest`
- **Design Reference:** `docs/AAOS_SCREEN_CONTRACT.md` rows 12–13 · `docs/AAOS_PRD.md` §7 rows 12–13 · `docs/aaos-DESIGN.md` · `docs/aaos-screens.html` (prototype)
- **Risk Tags:** UI states · lifecycle · performance (recomposition) · driver distraction compliance
- **Affected Modules:** `:automotive` (certain) · `:core:playback` (only if reorder is accepted)

## Problem

Screens 12 (`CarFullPlayerScreen`) and 13 (`CarQueueScreen`) exist and work, but were built
before the screen contract was settled. Three contract requirements are unimplemented, and one
of them is a compliance gap rather than a cosmetic one:

1. **The full player never shows a buffering state.** `PlaybackSnapshot.isBuffering` is
   populated by `BasePlayerStateCollector` and referenced **zero times** in
   `CarFullPlayerScreen.kt`. A driver on a slow connection sees a stalled, silent player with no
   indication anything is happening.
2. **The full player has no error overlay.** The contract lists one. Today a playback failure
   renders as a player that simply does not play.
3. **The queue is not truncated while driving.** Every browse and library list passes through
   `.take(maxItems)` from `restrictions.maxCumulativeContentItems`; the queue call site in
   `AutomotiveApp.kt:258` passes `playerState.playback.queue` raw. The PRD's row 13 says the
   list is truncated while driving.

A fourth item, **reorder**, appears in the contract ("remove/reorder controls while parked") and
does not exist anywhere in the codebase — no `onReorder`, no queue-move API in
`PlaybackQueueManager`. That is new capability, not a rebuild, and is called out as a decision
below rather than assumed into scope.

## User Impact

**Who:** every driver using the custom AAOS launcher — the `oem` flavor, which is the build
shipped to head units. The `playstore` flavor omits these activities entirely, so it is unaffected.

**What happens if we do nothing:**

- On a slow or dropped connection the player looks broken. There is no spinner, no message, and
  no error — a driver's only recourse is to guess whether to wait or press something. This is the
  same failure class A4 fixed for Favourites, where a loading state was indistinguishable from an
  empty one.
- A long queue stays fully scrollable while moving, which is the exact interaction pattern the
  distraction rules exist to bound. It is also inconsistent with every other list in the app, so
  the app's own behaviour teaches the driver a rule it then breaks.

## Scope

**Screen 12 — `CarFullPlayerScreen`**

- Render a buffering state driven by the existing `PlaybackSnapshot.isBuffering`.
- Render an error state per the decision taken on D-3 below.
- Keep every existing control working: artwork, title/artist, play/pause, prev/next, seek, like,
  shuffle, repeat, queue.

**Screen 13 — `CarQueueScreen`**

- Truncate the rendered queue to `maxCumulativeContentItems` while driving, matching the
  `.take(maxItems)` pattern already used at every other call site in `AutomotiveApp.kt`.
- Confirm and, where missing, complete the parked-vs-driving split the contract specifies:
  skip-to allowed in both states; remove, reorder and clear refused while driving. `isDriving`
  is already plumbed into `CarQueueScreen` and `QueueRow`, and `canClear = !isDriving` already
  holds — this is completion and verification, not new plumbing.
- Keep the existing empty, current-track and playing-indicator states.

**Both**

- Touch targets ≥76dp on every control, including any new one.
- Reuse existing colour tokens; no new pairs without a contrast measurement in `aaos-DESIGN.md`.

## Out Of Scope

- **The `playstore` flavor.** It does not ship `AutomotiveActivity` and must not start doing so
  — Play's AAOS media category rejects custom playback activities. Do not move these screens into
  the shared source set.
- **The OEM media template path.** `PlaybackService`, `MediaBrowseTree` and the
  `MediaLibrarySession` callbacks are a separate surface and must not change.
- **Screen 19 (`PlaybackError`) as a destination.** It belongs to A8.
- **The mobile app.** `:app`'s `PlayerViewModel`, `MiniPlayer` and `ExpandedPlayer` are not part
  of this slice. If reorder is accepted and forces a `:core:playback` change, the mobile side may
  need a follow-up — that is a separate ticket, not a silent widening of this one.
- **A4's parked residuals R1 and R2.** They live in `AutomotiveContentViewModel`; A5 touches
  `AutomotivePlayerViewModel`. Pull them in only by explicit decision.
- **The Favourites, Browse, Library and Detail screens** shipped in A3/A4.

## Acceptance Criteria

- Given a track is buffering, when the full player is open, then a buffering indicator is visible
  and the play/pause control reflects that playback has not started.
- Given buffering completes, when playback begins, then the buffering indicator is removed without
  the driver touching anything.
- Given playback fails, when the full player is open, then the failure is communicated on screen
  per D-3, and the player does not present as merely paused.
- Given the vehicle is parked, when the queue is open, then the full queue is listed and remove
  and clear controls are operable.
- Given the vehicle is moving, when the queue is open, then the list is truncated to
  `maxCumulativeContentItems`, and remove and clear are refused.
- Given the vehicle is moving, when a queue row is tapped, then playback skips to that track —
  skip-to remains allowed under restriction.
- Given a queue of one track, when the queue is open, then clear is unavailable in both states
  (existing `queue.size > 1` rule preserved).
- Given the driver unlikes the current track from the full player, when the heart is tapped, then
  its state flips and the media session's custom action flips with it.
- Given the process is killed while the full player is open, when the app is relaunched, then it
  returns without a crash and the player reflects actual playback state.
- Given `detekt` and `lint` run, then both pass with `maxIssues: 0` and no new baseline entries.

## Implementation Plan

Proposed starting point, not binding — the lead may revise during Round 0.

1. Render buffering in `CarFullPlayerScreen` from the snapshot field that already exists; no
   ViewModel change expected.
2. Decide D-3, then implement the error surface it selects.
3. Apply `.take(maxItems)` at the `CarQueueScreen` call site in `AutomotiveApp.kt`, matching the
   surrounding pattern; add the unit test that pins it.
4. Audit the queue's driving gating against the contract row and close whatever is missing.
5. Resolve D-2 before adding methods to `AutomotivePlayerViewModel`.
6. Reorder only if D-1 accepts it.

## Affected Areas

- **Modules:** `:automotive`; `:core:playback` only if reorder is accepted
- **Screens:** `CarFullPlayerScreen` (12), `CarQueueScreen` (13); `AutomotiveApp.kt` as the call site
- **APIs:** `AutomotivePlayerViewModel`, `PlaybackSnapshot` (consumed, not extended, unless reorder lands), `CarUxRestrictionsHandler`
- **Storage:** none expected — no Room entity, no schema, no preference

## Risk Areas

- **Security:** none identified. No new permission, exported component, token or IPC surface.
- **Performance:** the full player polls position at 250ms via `BasePlayerStateCollector`. A
  buffering indicator added inside that recomposition scope can cost a frame on every tick —
  keep the animating element in its own composable reading the narrowest state.
- **Lifecycle / process death:** both screens are conditional overlays, not nav destinations.
  A4 established that overlay state interacts with the shell's `LaunchedEffect(currentScreen)` —
  opening the full player fires `closeFavourites()`. Any new effect keyed on screen identity must
  be checked against that.
- **Compatibility:** `minSdk 29`, `targetSdk 35`, `compileSdk 35`. `android.car` APIs are
  `compileOnly` from the platform jar — code touching them cannot run off AAOS.
- **Data migration:** none.
- **UI states:** buffering, playing, paused, error on 12; empty, current-track, playing-indicator,
  parked and driving on 13. Driving truncation is the one most likely to be missed, because it is
  invisible unless restrictions are actually injected.

## Verification

**Command:** `./gradlew :automotive:testOemDebugUnitTest`

Full gate, matching A3/A4:

```
./gradlew :automotive:testOemDebugUnitTest
./gradlew detekt
./gradlew :automotive:lintOemDebug
./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug
```

Existing test files to extend rather than duplicate: `DetailLoadingTest.kt`,
`FavouritesSnapshotTest.kt`, `UxFlagsTest.kt`.

**Manual / device checks** — `AAOS_AOSP_33_userdebug`, **one emulator only**, per
`docs/AAOS_A3_VERIFICATION.md`:

- Parked: every control on both screens tapped, none silently dead. A3 shipped a dead card that
  nine reviews missed and one tap found.
- Driving injected via `cmd car_service inject-vhal-event 0x11400400 8` plus
  `inject-continuous-events 0x11600207 40 -s 5 -d 60` — reaches `DO: true UxR: 255`. Confirm the
  queue truncates and edit controls refuse.
- **Truncation needs a queue longer than the cap.** With 12 liked songs nothing truncates, which
  is why A4 could not observe this. Build a long queue first or the check is vacuous.
- Buffering observed by throttling: `adb emu network speed gsm` and `network delay gprs`.
- Process death with the full player open: stop playback first — `am kill` is refused while the
  foreground playback service holds the process, and the PID will not change.

## Human Decisions Needed

- **D-1 — Reorder: implement or amend the contract?** It exists in no form today and needs a
  queue-move API on `PlaybackQueueManager` in `:core:playback`, which mobile also consumes. That
  makes it the largest item here by some distance, and it is the one most defensible to drop:
  reordering a queue is a parked-only convenience. Recommend deferring it and amending the
  contract row, but this is a product call.
- **D-2 — Split `AutomotivePlayerViewModel` or suppress again?** It is at **21 functions** and
  already carries a `TooManyFunctions` suppression; detekt's `thresholdInFiles` is 20. A4's D23
  recorded that the next slice touching a suppressed ViewModel should split rather than suppress
  — I read that as aimed at `AutomotiveContentViewModel`, but A5 lands on the player one. Decide
  before implementation, not when the build fails.
- **D-3 — Where does a playback error surface?** Inline overlay on screen 12, or defer entirely
  to screen 19 (`PlaybackError`) in A8? The contract says screen 12 has an error overlay; the PRD
  says errors route to 19. **These two documents disagree**, and it needs settling before anyone
  builds against either.
- **D-4 — Should a playback queue be truncated at all?** `maxCumulativeContentItems` bounds
  *browsable content*. A playback queue is what is already playing, not something being browsed,
  and hiding its tail may be the wrong reading of the rule even though the PRD asserts it. Worth
  a deliberate answer, since it changes what "correct" means for the third problem above.

## Notes

`docs/AAOS_PRD.md` §9's phasing table has been corrected through A5. The remaining follow-up
from A5 verification is tracked separately in `docs/tickets/T3-automotive-playback-restore.md`.
