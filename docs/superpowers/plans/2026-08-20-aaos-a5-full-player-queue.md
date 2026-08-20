# AAOS Slice A5 — Full Player & Queue Implementation Plan

> **For agentic workers:** Implement task-by-task. Steps use checkbox (`- [ ]`) syntax for
> tracking. Do not skip the verification gates at the end.

**Goal:** Complete the existing full-player and queue overlays against the AAOS screen contract:
buffering is visible, playback errors are deliberately surfaced, and the queue obeys driving
restrictions without corrupting Media3 queue indices.

**Spec:** `docs/superpowers/specs/2026-08-20-aaos-full-player-queue-design.md`

**Tech stack:** Kotlin, Jetpack Compose, Media3, Hilt, JUnit 4, kotlinx-coroutines-test, Gradle
Kotlin DSL, Detekt, Lint.

## Global constraints

- **Max line length 120.** Trailing commas required on call and declaration sites. No wildcard
  imports.
- **Detekt `maxIssues: 0`.** Its configured source set covers production Kotlin; do not rely on
  test-source linting for production quality.
- **Top-level constants are PascalCase.**
- **Composables emitting UI take `modifier: Modifier = Modifier` as the first optional
  parameter.**
- **Automotive touch targets are at least 76dp.** Reuse `CarTouchTargetSize`,
  `carTouchTarget()`, `SkipButtonSize`, or `PlayButtonSize` rather than inventing a smaller size.
- **No new color pairs** unless measured and recorded in `docs/aaos-DESIGN.md`.
- **Flavored Gradle tasks:** use `:automotive:assembleOemDebug`,
  `:automotive:testOemDebugUnitTest`, `:automotive:lintOemDebug`; the plain `Debug` variants do
  not exist.
- **Verify builds from Gradle's own `BUILD SUCCESSFUL` line.**
- **Do not touch the `playstore` custom-activity boundary** or the OEM media-template path.

## Sequencing

Task 1 is the risky logic: queue truncation changes what is rendered while callbacks still need to
address the real queue. It lands with unit tests before the screen consumes it. Task 2 wires it.
Task 3 adds buffering UI. Task 4 verifies the already-existing error overlay contract. Task 5
records decisions and verification.

## File structure

**Created**

| File | Responsibility |
|---|---|
| `automotive/.../ui/screens/QueueDisplayItem.kt` | Pure display-window helper and index mapping for queue rows |
| `automotive/src/test/.../ui/screens/QueueDisplayItemTest.kt` | Unit tests for queue cap/window/index mapping |
| `docs/AAOS_A5_VERIFICATION.md` | Manual verification record after device checks |

**Modified**

| File | Change |
|---|---|
| `automotive/.../ui/screens/CarQueueScreen.kt` | Consume `QueueDisplayItem`, show capped queue, map callbacks to original indices, fix helper copy |
| `automotive/.../ui/AutomotiveApp.kt` | Pass capped queue parameters into `CarQueueScreen` |
| `automotive/.../ui/screens/CarFullPlayerScreen.kt` | Render buffering state from `PlaybackSnapshot.isBuffering` |
| `automotive/.../ui/preview/CarScreenPreviews.kt` | Add or update previews only if signatures change |
| `docs/aaos-DESIGN.md` | Record D26-D30 |
| `docs/AAOS_PRD.md` | Keep phase/status table current if implementation lands |

---

## Task 1: Queue display window helper

The queue list is capped while driving, but callbacks must still operate on the original queue
indices. Put that mapping in one pure helper and test it before touching Compose.

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/QueueDisplayItem.kt`
- Create: `automotive/src/test/java/com/example/nyasaplayer/auto/ui/screens/QueueDisplayItemTest.kt`

**Interfaces:**

```kotlin
internal data class QueueDisplayItem(
    val song: Song,
    val queueIndex: Int,
)

internal fun queueDisplayItems(
    queue: List<Song>,
    currentIndex: Int,
    maxItems: Int,
    isDriving: Boolean,
): List<QueueDisplayItem>

internal fun canClearQueue(
    queueSize: Int,
    isDriving: Boolean,
): Boolean
```

- [ ] Implement parked behavior: returns every queue row with its original index.
- [ ] Implement driving behavior: returns at most `maxItems.coerceAtLeast(0)` rows.
- [ ] If the current item is within the queue but outside the first capped page, return a capped
      window that includes it.
- [ ] If `currentIndex` is invalid, fall back to the first capped page.
- [ ] Implement `canClearQueue(queueSize, isDriving)` as `!isDriving && queueSize > 1`.
- [ ] Unit-test parked, short driving queue, long driving queue, current-beyond-cap, invalid
      current index, zero/negative cap, index mapping, and clear gating.

**Verify:** `./gradlew :automotive:testOemDebugUnitTest`

---

## Task 2: Wire queue truncation into `CarQueueScreen`

The screen should receive enough information to render the queue window while callbacks still
target the real queue.

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarQueueScreen.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/preview/CarScreenPreviews.kt` only if needed

- [ ] Add a `maxItems: Int` parameter to `CarQueueScreen`.
- [ ] Derive `displayItems = queueDisplayItems(queue, currentIndex, maxItems, isDriving)`.
- [ ] Render `displayItems` rather than the raw queue.
- [ ] Pass `displayItem.queueIndex` to `onSkipTo` and `onRemove`.
- [ ] Keep `upcomingCount` and `upcomingDurationMs` based on the real queue, not the capped
      display rows.
- [ ] Replace clear gating with `canClearQueue(queue.size, isDriving)`.
- [ ] Update the driving helper copy from "reorder or clear" to wording that reflects shipped
      actions, e.g. "Park the car to remove or clear your queue."
- [ ] In `AutomotiveApp`, pass `playerState.restrictions.maxCumulativeContentItems` into
      `CarQueueScreen`.

**Verify:** `./gradlew :automotive:assembleOemDebug :automotive:testOemDebugUnitTest`

---

## Task 3: Render buffering in the full player

Buffering is already present in `PlaybackSnapshot`; this task is UI-only.

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/CarFullPlayerScreen.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/preview/CarScreenPreviews.kt` only if useful

- [ ] Add a small buffering indicator near the main transport controls.
- [ ] Render it only when `playback.isBuffering` is true.
- [ ] Keep play/pause, skip, seek, queue, like, shuffle and repeat available.
- [ ] Use existing colors and touch-target sizes.
- [ ] Keep any animated indicator isolated in a narrow composable.
- [ ] Add/update a preview with `PlaybackSnapshot(isBuffering = true)` if preview coverage exists
      nearby.

**Verify:** `./gradlew :automotive:assembleOemDebug :automotive:lintOemDebug detekt`

---

## Task 4: Lock the playback-error overlay contract

The production code already renders `CarErrorOverlay` after `CarFullPlayerScreen` and
`CarQueueScreen`, so A5 should document and verify that shape rather than inventing another error
surface.

**Files:**
- Modify production code only if a build or manual check proves the overlay is hidden or wrong.
- Otherwise no production files are expected in this task.

- [ ] Confirm `AutomotivePlayerViewModel.onPlaybackError` sets a retryable `PlayerError`.
- [ ] Confirm `AutomotiveApp` renders `CarErrorOverlay` after full player and queue overlays.
- [ ] Confirm Retry remains hidden for non-retryable errors such as failed like sync.
- [ ] Do not create a screen-19 destination in A5.

**Verify:** code review plus manual A5 checklist. If a test hook is available after T1, add a
rendering test; otherwise record the gap in `docs/AAOS_A5_VERIFICATION.md`.

---

## Task 5: Record A5 decisions

**Files:**
- Modify: `docs/aaos-DESIGN.md`
- Modify: `docs/AAOS_SCREEN_CONTRACT.md` if wording still promises reorder as a shipped A5 action
- Modify: `docs/AAOS_PRD.md` status table after implementation lands

- [ ] Record D26-D30 from the spec in `docs/aaos-DESIGN.md`.
- [ ] Remove or qualify queue reorder wording until a real reorder API exists.
- [ ] Keep the PRD phase table honest: A5 should move from "Draft spec for review" only after
      implementation and verification land.

**Verify:** docs diff review.

---

## Task 6: Full verification and record

**Files:**
- Create: `docs/AAOS_A5_VERIFICATION.md`

- [ ] Run `./gradlew :automotive:testOemDebugUnitTest`.
- [ ] Run `./gradlew detekt`.
- [ ] Run `./gradlew :automotive:lintOemDebug`.
- [ ] Run `./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug`.
- [ ] On `AAOS_AOSP_33_userdebug`, run the manual checklist from the spec.
- [ ] Use a queue longer than the reported cap, or the driving truncation check is vacuous.
- [ ] Record verified, not verified, and observations in `docs/AAOS_A5_VERIFICATION.md`, matching
      the A3/A4 style.

**Definition of done:** all A5 spec §8 items are satisfied, and no new unrecorded contract
deviation remains.
