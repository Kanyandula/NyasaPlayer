# AAOS T5 + T6 — verification record

Covers the two post-A6 structural tickets, implemented as a stacked PR sequence per
`docs/superpowers/plans/2026-08-21-aaos-t5-t6-navigation-content-cap.md`.

- **Date:** 2026-08-21
- **Branches:** `ek/aaos-t6-navigation-state` (PR #31) → `ek/aaos-t5-content-cap` →
  `ek/aaos-t5-t6-verification-docs`
- **AVD:** `AAOS_AOSP_33_userdebug` (API 33, `userdebug`), one emulator only
- **Build:** `oem` debug APK, installed for user 10 (the driver)
- **Account:** the real signed-in user, against live Firestore — not a fake
- **Reported cap:** `maxCumulativeContentItems = 21`, the same value A5 and A6 recorded

## Gates

| Command | Result |
|---|---|
| `./gradlew :automotive:testOemDebugUnitTest` | Pass — 126 tests, 0 failures, 0 errors |
| `./gradlew :core:data:testDebugUnitTest` | Pass — 54 tests, 0 failures, 0 errors |
| `./gradlew detekt` | Pass — no new baseline entries |
| `./gradlew :automotive:lintOemDebug` | Pass |
| `./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug` | Pass — both flavors |

Each stack layer also passed a pre-PR code review against its own diff range, per the plan.
Correctness, quality and simplification reviewers ran on
`main...ek/aaos-t6-navigation-state`; correctness returned no Critical or Major findings and all
findings were applied rather than accepted as risk.

## T6 — pointer-through, answered before the navigation edit

The plan required this question settled first, because the answer decides whether some teardown
calls are dead code. **Both full-screen surfaces leaked touches to the shell underneath.**

| Surface | Method | Result |
|---|---|---|
| `CarSearchScreen` | From a confirmed Home, opened search, tapped (40, 373) — empty sheet space over the Library rail button | **Leaked** — tab switched to Library and the sheet closed |
| `CarQueueScreen` | From a confirmed Home, opened the queue from the mini player, tapped (8, 373) — the strip beside the rows, over the same button | **Leaked** — landed on Library after closing |

Drawing over `BrowseShell` does not stop Compose delivering touches to it: hit-testing collects
every overlapping pointer-input node at a coordinate regardless of Z-order.

Mitigation is `carConsumeTouches()` (`ui/components/CarPrimitives.kt`), consuming on the **main**
pass so each surface's own children handle their touches first. Re-verified on device in both
directions: the rail tap now does nothing, and the sheet's own `Genres` shortcut still routes to
Browse. Deliberately not a no-op `clickable`, which announces an interactive control that does
nothing (FR-2.6) — the review then found two modal cards already doing exactly that and converted
them, so the codebase has one idiom for this rather than two.

The first two queue attempts were **inconclusive and are not counted**: one tap landed on a queue
row and skipped tracks, and one started from Library, so "still Library" proved nothing. Only the
run that screenshot-confirmed Home beforehand is recorded above.

## T6 — overlay stack

| Check | Result |
|---|---|
| Queue opens above the full player | Pass |
| Closing the queue returns to the full player, not the browse shell | Pass |
| Overlay stack survives process death | Pass — see below |

Process death followed the A4/A5 recipe: paused playback, backgrounded, then `am kill --user 10`.
PID went 5110 → 5405, so the kill landed. On relaunch the queue was on top, and closing it
revealed the full player — the stack round-tripped as `[FullPlayer, Queue]` rather than
flattening to `[Queue]`, which is what the explicit `listSaver` exists for.

The restored player is empty (no track, 0:00) because `:automotive` never restores playback
state. That is A5's recorded carve-out, unchanged by T6.

**Correction to the A4/A5 note on `am kill`:** it says the kill is silently refused while playback
holds the foreground service, leaving the PID unchanged. Here the PID *changed* and the process
was alive again seconds later — the kill landed and the media service was immediately re-bound.
"The process is alive" is not evidence the kill failed; compare the PID.

## T5 — content cap: **not non-vacuously verified on device**

This is the one gap in this record, stated plainly rather than reported as a pass.

**What was not done.** The plan asks for an emulator smoke test with "a library larger than the
reported cap": parked lists should scroll past the cap, driving lists should stop at it.

**Why.** No list in this account exceeds 21 — 13 liked songs, 7 genres, 0 albums, and Home's rows
are capped to 12 and 8 by `AutomotiveContentViewModel` before the restriction cap is applied at
all. Parked and driving therefore render identically, so the check would pass without
demonstrating anything. `cmd car_service` offers only `enable-uxr true|false` and cannot lower
`maxCumulativeContentItems`, so the cap cannot be brought under the data instead.

The remaining way to make it non-vacuous was to write synthetic liked songs into the real account.
**Deliberately declined:** verification should not depend on mutating real user data, and the
behaviour is covered below.

**Evidence accepted in its place.**

- A6's device run proved the shared `cap()` path end to end: `searchSongs()` returned 41 rows for
  `worship`, and while driving the media session queue was `size=21` with `active item id=2`
  matching the tapped row. T5 routes more call sites through that same function.
- `RestrictionCapTest` covers parked-uncapped, driving-capped, cap-above-count and a negative cap;
  replacing the body with a bare `take` fails two named tests.
- `ArtistLikedSongsTest` covers the one ordering that is real logic — the artist drill-down
  filters before capping. Reversing it fails `the cap applies to the artist's songs, not to the
  library it filtered them from`.

**Remaining risk.** `BrowseShell`'s newly routed lists — Home, Browse, Library root, the artist
drill-down, album/playlist detail and Favourites — were never observed truncating or not
truncating on device against an over-cap dataset. The rule they now call is proven; their wiring
to it is proven only by JVM tests and by reading the diff. Re-run the smoke test on any account
whose liked songs exceed the reported cap.

**Also not measured:** whether removing the parked caps costs scroll performance. The plan asks
for this to be documented rather than reverted if a screen janks; with every list under 21 there
was no case where the parked list is larger than what was previously rendered, so there was
nothing to observe. Same account-data limitation.

## Observations

**`gh stack` is not built in.** The plan originally said it was, on the strength of
`gh stack --help` exiting 0 — it prints "available as an official extension" and exits 0 anyway,
so a `&&` presence check reports success for a command that does not exist. Corrected in PR #30;
plain branches were used for this stack.

**The emulator's car stack wedged after ~10h45m uptime.** `CarLauncher` crashed with a
`NullPointerException` out of `Car.getCarManager` and `car_service` itself ANR'd, which left the
app unable to hold focus. `MemAvailable` was a healthy 609MB at the time, so memory is not a
sufficient health check — the car stack can fail independently. A cold boot fixed it.

**`adb emu kill` is asynchronous.** Clearing `multiinstance.lock` immediately afterwards races the
dying process, which recreates it, and the next launch dies with "Running multiple emulators with
the same AVD". Wait for the `qemu-system` process to disappear before clearing the lock.
