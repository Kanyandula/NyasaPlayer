# AAOS Slice A5 — Full Player and Queue

> **Status:** Draft for review · **Date** 2026-08-20 · **Depends on:** A2 (merged, PR #16), A3 (merged, PRs #18-#20), A4 (merged, PRs #21-#23)
> **Design source:** `docs/aaos-DESIGN.md` §Layout, §Components, §Driving restrictions
> **Scope source:** `docs/AAOS_PRD.md` §9 (phase A5), `docs/AAOS_SCREEN_CONTRACT.md` screens 12 and 13, `docs/tickets/A5-full-player-queue.md`

## 1. Context

A5 finishes the two playback overlays that A2 left structurally present but not contract-complete:

- `CarFullPlayerScreen` — exists, exposes transport controls, like, seek, shuffle, repeat and queue.
- `CarQueueScreen` — exists, exposes skip-to, remove and clear, and already distinguishes parked
  from driving for edit actions.

Both screens sit outside the chrome as full-screen overlays. That is intentional: the mini-player
is the chrome entry point, and the overlay then owns the whole head-unit surface for the focused
playback task.

### 1.1 What A5 is

1. Full player buffering state, driven by `PlaybackSnapshot.isBuffering`.
2. Full player playback-error surface, using the existing `PlayerError` channel and car overlay
   behavior rather than a new navigation destination.
3. Queue truncation while driving, capped by `maxCumulativeContentItems`.
4. Queue action audit: skip-to remains allowed while driving; remove and clear stay parked-only.
5. Verification that process death, queue index mapping, and current-track indication still hold.

### 1.2 What A5 is not

- **Not queue reorder.** Reorder has no queue-move API and would widen into `:core:playback` and
  the mobile app. See D26.
- **Not the A8 playback-error destination.** A5 surfaces playback errors over the full player;
  A8 can later refine screen 19's visual treatment.
- **Not the OEM media template path.** `PlaybackService`, `MediaBrowseTree` and media-session
  custom actions remain separate surfaces.
- **Not search, settings, downloads, profile switching, or auth.** Those remain A6, A7 and A8.
- **Not a mobile-player rewrite.** `:app` player screens are unaffected unless a future queue API
  change is deliberately accepted.

## 2. Problems

### 2.1 Buffering is invisible

`PlaybackSnapshot.isBuffering` is populated by `BasePlayerStateCollector.onPlaybackStateChanged`,
and cleared on `onPlayerError`, but `CarFullPlayerScreen` never reads it. A slow stream therefore
looks paused or broken instead of explicitly waiting on the network.

### 2.2 Playback errors leave the full player ambiguous

`AutomotivePlayerViewModel` already maps `PlaybackException` into `PlayerError`, and
`AutomotiveApp` already renders `CarErrorOverlay` for that channel. A5's gap is not that no error
path exists; it is that the full player contract says screen 12 owns an error state while the PRD
says playback errors route to screen 19. The implementation must make the current behavior
intentional and verify it instead of letting the documents disagree.

### 2.3 The queue ignores the item cap while driving

Every content list rendered inside the AAOS shell is capped from
`restrictions.maxCumulativeContentItems`; the queue overlay currently receives
`playerState.playback.queue` raw. While driving, that can leave a long editable-looking list on
screen even though edit actions are locked.

## 3. Screen 12 — `CarFullPlayerScreen`

### 3.1 Buffering

When `playback.isBuffering` is true:

- Render a visible buffering state near the central transport controls.
- Keep the play/pause control available and readable.
- Do not replace title, artist, album art, progress, queue, like, shuffle or repeat.
- Avoid putting an animated element inside a broad recomposition scope; the position poll updates
  every 500ms in `AutomotivePlayerViewModel`, so the buffering indicator should read only the
  boolean it needs.

The state is informational, not a modal. A driver should understand that playback is waiting, but
should not lose access to skip, queue, collapse, or retry affordances.

### 3.2 Playback error

Playback failures continue through `AutomotivePlayerViewModel.error` and the existing
`CarErrorOverlay` in `AutomotiveApp`. For A5 this counts as the screen 12 error overlay: it appears
above the full player when the full player is open, can be dismissed while driving, and exposes
Retry only when `PlayerError.isRetryable` is true.

Do not add a second destination or a separate error state store. Duplicating the channel creates
two places to clear, retry and distinguish playback errors from non-playback errors.

### 3.3 Existing controls

A5 must preserve the existing control set:

- collapse
- queue
- seek
- play/pause
- previous/next
- shuffle
- repeat
- like/unlike

All touch targets remain at least 76dp. The existing full-player controls already use
`CarTouchTargetSize`, `SkipButtonSize` or `PlayButtonSize`; any new control must follow the same
floor.

## 4. Screen 13 — `CarQueueScreen`

### 4.1 Driving cap

When the vehicle is driving, the queue renders at most
`restrictions.maxCumulativeContentItems` rows. When parked, it renders the full queue.

The cap is applied to the rendered rows, not the playback queue itself. Playback state and skip-to
must continue to address the real Media3 queue.

### 4.2 Current-track visibility

The queue's required states include the current-track and playing indicators, so truncation must
not blindly hide the current item when `currentQueueIndex` is beyond the cap.

If the queue is longer than the cap and the current item would fall outside a simple prefix, render
a capped window that includes the current item. Row callbacks must map displayed rows back to their
original queue indices.

### 4.3 Action gating

- Skip-to row tap remains available while driving.
- Remove is disabled while driving.
- Clear is disabled while driving.
- Clear also remains disabled when the queue has one item, preserving the existing rule.
- The driving helper chip may remain, but its copy must not promise reorder until reorder exists.

## 5. Verification

### 5.1 Unit tests

The project still lacks a Compose rendering harness; T1 owns that. A5 should therefore put the
highest-risk logic behind pure functions where possible and test those functions on the JVM.

Required cases:

1. Parked queue renders the full queue.
2. Driving queue shorter than the cap renders the full queue.
3. Driving queue longer than the cap renders no more than the cap.
4. Driving truncation includes the current item when it is beyond the first cap rows.
5. Displayed queue rows map skip-to and remove callbacks back to original queue indices.
6. Clear is available only when parked and `queue.size > 1`.
7. The global gate still allows Full Player and Queue locations while driving; action gating stays
   in the screen/call-site layer.

### 5.2 Manual checklist

Run on `AAOS_AOSP_33_userdebug`, one emulator only, per `docs/AAOS_DRIVING_STATE_TESTING.md` and
the A3/A4 verification notes.

1. Parked: open full player; every control responds.
2. Throttle the network (`adb emu network speed gsm`, `network delay gprs`): buffering is visible
   while playback is waiting and disappears when playback resumes.
3. Force or observe a playback failure: error overlay appears above the full player and Retry only
   appears for retryable playback errors.
4. Parked queue: full queue visible; skip-to, remove and clear work.
5. Driving queue with more rows than the cap: list truncates, current track remains visible, and
   skip-to still works.
6. Driving queue: remove and clear are visibly disabled/refused; no silent no-op.
7. Queue of one: clear remains unavailable in both parked and driving states.
8. Process death with full player open: relaunches without crash and reflects actual playback
   state. Stop playback before `am kill`, or the foreground service keeps the process alive.

### 5.3 Gates

Run the same gates A3/A4 used:

```bash
./gradlew :automotive:testOemDebugUnitTest
./gradlew detekt
./gradlew :automotive:lintOemDebug
./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug
```

No new Detekt baseline entries. No new color pairs unless measured in `docs/aaos-DESIGN.md`.

## 6. Decisions

Numbering continues A4's D19-D25.

| # | Decision | Rationale |
|---|---|---|
| D26 | **Queue reorder is deferred and removed from A5 scope.** A5 may keep copy that says reorder is parked-only only if the UI actually exposes reorder later; until then, copy should say park to remove or clear. | Reorder has no API in `PlaybackQueueManager`, no ViewModel method, and no mobile contract. Adding it is a queue-core feature, not completion of the existing full-player/queue overlays. It is also parked-only convenience, while buffering, error and driving truncation are correctness and compliance work. |
| D27 | **Screen 12's error overlay is the existing global `CarErrorOverlay`, not a new destination.** | `AutomotivePlayerViewModel` already routes playback failures into `PlayerError`, and `AutomotiveApp` already renders an overlay above the current screen. This satisfies the screen-contract state without creating screen 19 as a destination inside A5. A8 can refine the dedicated playback-error visual later. |
| D28 | **Queue truncation uses a capped display window, not destructive queue mutation.** | `maxCumulativeContentItems` restricts what is visible while driving. It must not alter Media3 queue state or persisted playback order. A display window also lets A5 preserve the current-track indicator when the current item is beyond the first cap rows. |
| D29 | **A5 does not split `AutomotivePlayerViewModel` unless implementation adds player APIs.** | The current A5 scope can consume existing state and methods: buffering is already in `PlaybackSnapshot`, playback errors are already in `PlayerError`, and queue skip/remove/clear already exist. If implementation discovers a new public player method is genuinely required, split first rather than adding another suppression. |
| D30 | **Queue truncation applies while `requiresDistractionOptimization` is true.** | Existing call sites use the derived driving/restriction state, and A4 verified injected moving state reaches `DO: true UxR: 255`. A5 follows that model so queue behavior changes with the same state that locks edit actions. |

## 7. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| A displayed queue index is passed directly to Media3 after truncation/windowing | Driver taps one row and playback jumps to a different track | Represent displayed rows with their original queue indices; unit test index mapping |
| Current track disappears from a capped queue | Required current-track state is invisible while driving | Use a capped window that includes `currentQueueIndex`; unit test current beyond cap |
| Buffering indicator recomposes with every position tick | Frame cost on a full-screen overlay | Isolate the indicator in a narrow composable reading `isBuffering` only |
| Error overlay behavior is assumed but not checked in the full-player path | Playback failure still presents as a silent pause | Manual A5 checklist includes playback failure while full player is open |
| Reorder stays in copy after being deferred | UI promises an action that does not exist | Update helper text/contract wording when implementing A5 |

## 8. Definition of done

1. `CarFullPlayerScreen` visibly represents `playback.isBuffering`.
2. Playback errors shown while the full player is open render through `CarErrorOverlay`, with Retry
   only for retryable playback errors.
3. `CarQueueScreen` renders no more than `maxCumulativeContentItems` rows while driving.
4. Driving queue truncation preserves current-track visibility.
5. Queue row actions map displayed rows to original queue indices.
6. Skip-to works while driving; remove and clear remain parked-only.
7. Queue clear remains unavailable for a one-item queue in all driving states.
8. No A5 change touches the `playstore` custom-activity boundary or the OEM media-template path.
9. D26-D30 are recorded in `docs/aaos-DESIGN.md`.
10. The A5 manual checklist is executed and recorded in `docs/AAOS_A5_VERIFICATION.md`.
11. Both flavors build; unit tests pass; lint and Detekt are clean.
