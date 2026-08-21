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

Each stack layer passed a pre-PR code review against its own diff range, per the plan.
`main...ek/aaos-t6-navigation-state` drew correctness, quality and simplification reviewers;
`ek/aaos-t6-navigation-state...ek/aaos-t5-content-cap` drew correctness and simplification; this
layer drew a claim-by-claim fact check. Correctness returned no Critical or Major findings on
either code layer. Every finding was applied rather than accepted as risk — including several
against this document, which is why the paragraphs above read as they do.

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
flattening to `[Queue]`.

The explicit `listSaver` is not what made that round-trip work: Compose's `canBeSavedToBundle`
accepts anything `Serializable`, which every list produced here is, so the default `autoSaver`
would have stored it. The saver earns its place on the *restore* side, tolerating an enum name a
later build no longer has instead of throwing out of `valueOf` mid-restore.

The restored player is empty (no track, 0:00) because `:automotive` never restores playback
state. That is A5's recorded carve-out, unchanged by T6.

This **confirms** A4's recipe rather than correcting it. A4 already records both halves — the kill
is refused while playback holds the foreground service, and after stopping playback first it saw
`9218` → `10068` — as did A5 (`6984` → `7789`). Our run followed that mitigation, so it never met
the condition the warning is about, and the changed PID is what A4 predicts. Worth one note for the
next reader: the process was alive again seconds later, which surprised me until I compared PIDs.
Why it restarted was not investigated; a re-bound media service is a guess, not an observation.

## T5 — content cap: **not non-vacuously verified on device**

This is the one gap in this record, stated plainly rather than reported as a pass.

**What was not done.** The plan asks for an emulator smoke test with "a library larger than the
reported cap": parked lists should scroll past the cap, driving lists should stop at it.

**Why.** The lists that could be counted are all under 21 — 13 liked songs, 7 genres, 0 albums, and
Home's rows are capped to 12 and 8 by `AutomotiveContentViewModel` before the restriction cap
applies at all. **Playlists and playlist-detail tracks were not counted**: they are Firestore-backed
rather than in Room, so the sqlite check that covered the others does not reach them. T5 routes
them too, so "nothing here is over-cap" holds for every list except those two. Parked and driving
therefore render identically, and the check would pass without demonstrating anything.
`cmd car_service` drives restriction *state* well — A6 reached `DO: true UxR: 255` with
`inject-vhal-event` and `inject-continuous-events` — but nothing in its help output lowers
`maxCumulativeContentItems`, so the cap could not be brought under the data that way.

Two routes to a non-vacuous check existed and neither was taken:

- **Writing synthetic liked songs into the real account.** Deliberately declined: verification
  should not depend on mutating real user data.
- **Temporarily clamping the cap in a local debug build.** `toUxState()` (`viewmodel/UxFlags.kt`)
  is the one pure function the value passes through, so a one-line clamp to, say, 3 would put the
  liked songs, recently-played and genres all over-cap, exercise every newly routed list on device,
  and touch no user data. This was not thought of at the time. **It is the check the remaining-risk
  paragraph asks a future reader to run, and it is cheaper than waiting for an over-cap account.**

**Evidence accepted in its place.**

- A6's device run proved the shared `cap()` path end to end: `searchSongs()` returned 41 rows for
  `worship`, and while driving the media session queue was `size=21` with `active item id=2`
  matching the tapped row. T5 routes more call sites through that same function.
  **Caveat found while fact-checking this record:** A6's checklist row 4 is labelled *Parked* but
  records that same `size=21` / `active item id=2` observation. `cap()` was already conditional at
  A6, so a genuinely parked run would have queued 41 — the row is mislabelled and reuses the
  driving observation. A6 therefore never verified the parked direction either, which means
  **no device run has yet shown a parked list going uncapped.** Correction to the A6 record is
  tracked separately; the driving evidence above is unaffected.
- `RestrictionCapTest` covers parked-uncapped, driving-capped, cap-above-count and a negative cap;
  replacing the body with a bare `take` fails three named tests across the suite.
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
so a `&&` presence check reports success for a command that does not exist. PR #30 corrects it but is still open, so
the plan file on this branch still carries the wrong sentence. Plain branches were used here.

**The emulator's car stack wedged after ~10h45m uptime.** `CarLauncher` crashed with a
`NullPointerException` out of `Car.getCarManager` and `car_service` itself ANR'd, which left the
app unable to hold focus. `MemAvailable` was a healthy 609MB at the time, so memory is not a
sufficient health check — the car stack can fail independently. A cold boot fixed it.

**`adb emu kill` is asynchronous.** Clearing `multiinstance.lock` immediately afterwards races the
dying process, which recreates it, and the next launch dies with "Running multiple emulators with
the same AVD". Wait for the `qemu-system` process to disappear before clearing the lock.
