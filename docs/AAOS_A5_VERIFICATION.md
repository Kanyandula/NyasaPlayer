# AAOS Slice A5 — verification record

Closes definition-of-done items 10 and 11 of
`docs/superpowers/specs/2026-08-20-aaos-full-player-queue-design.md`: the §5.2 manual checklist
executed and its outcome recorded, plus the §5.3 gates.

- **Date:** 2026-08-20
- **Branch:** `ek/aaos-a5-t1-queue-window`
- **AVD:** `AAOS_AOSP_33_userdebug` (API 33, `userdebug`), one emulator only
- **Build:** `oem` debug APK, installed for user 10 (the driver)
- **Account:** the real signed-in user, against live Firestore — not a fake
- **Queue under test:** 139 songs from a genre shuffle, against a reported cap of 21
  (`Max Cumulative Content Items: 21` from `CarUxRestrictionsManagerService`), so no
  truncation check below is vacuous

## Gates

| Command | Result |
|---|---|
| `./gradlew :automotive:testOemDebugUnitTest` | Pass — 66 tests, 0 failures, 0 errors |
| `./gradlew detekt` | Pass — no new baseline entries |
| `./gradlew :automotive:lintOemDebug` | Pass |
| `./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug` | Pass — both flavors |

The suite only runs with two **untracked, pre-existing** test files moved aside:
`FavouritesBoundaryTest.kt` and `FavouritesJourneyTest.kt` reference fake members that do not
exist (`likedSongsFlowError`, `favouritesError`, `likedFor`), and their 38 compile errors take
down the whole `:automotive` unit-test source set. They are not part of A5 and were restored
untouched after each run.

## Manual checklist (§5.2)

| # | Check | Result |
|---|---|---|
| 1 | Parked: full player opens; every control responds | Pass — play/pause, skip, seek, shuffle, repeat, like and queue all acted |
| 2 | Buffering visible while playback waits, gone when it resumes | Pass **after a fix** — see below |
| 3 | Playback failure: error overlay above the full player, Retry only when retryable | **Partly** — overlay and Retry-gating observed for a non-retryable error; a retryable playback failure could not be forced on the emulator, see below |
| 4 | Parked queue: full queue visible; skip-to, remove and clear work | Pass — 139 rows, skip-to hit the tapped song, remove took the named song, clear enabled |
| 5 | Driving queue longer than the cap: truncates, current stays visible, skip-to works | Pass — see "The index-mapping proof" |
| 6 | Driving: remove and clear visibly refused, no silent no-op | Pass — clear pill reads "Locked" and dims, row menus dim, helper chip states the reason; tapping either changed nothing (queue still 138) |
| 7 | Queue of one: clear unavailable parked and driving | Pass — dimmed "Clear Queue" parked, "Locked" driving, tap did nothing |
| 8 | Process death with full player open: relaunches without crash, reflects playback state | Pass on crash; **empty player on return** — see below |

Driving state was the real thing throughout: `inject-vhal-event 0x11400400 8` plus
`inject-continuous-events 0x11600207 40` gave `DO: true UxR: 255`, and parking returned
`DO: false UxR: 0`.

### The index-mapping proof

This is the check the whole slice exists for, and a capped list only proves it when the current
track sits past the cap:

1. Parked, skipped to a track deep in the queue. The header's upcoming count (75) fixes the
   current index at **62** — far beyond the cap of 21.
2. Started driving. The list re-anchored so its **first row is the current track** ("U r alpha
   and omega", gold border), followed by queue indices 63, 64, 65. The rows above it, visible a
   moment earlier while parked, were gone.
3. Tapped the **third** row of that window. Playback moved to "everyday everyhour" — queue index
   **64**, exactly that row's song. Had the display index been passed through, index 2 would have
   played instead.
4. Throughout, `dumpsys media_session` reported `queueTitle=null, size=138` — the Media3 queue
   was never mutated by truncation (D28).

### Buffering was fixed because of this run

The first implementation drew the `CircularProgressIndicator` at `PlayButtonSize` in
`NyasaOnGold`, i.e. dark-on-gold **on top of** the play button. On the emulator it read as a dark
smudge on the button's edge — technically present, practically invisible at a glance, which is
the only glance a driver gets. It now draws at `PlayButtonSize + 16dp` in `NyasaGold`, outside
the button against the dark background, and uses `requiredSize` inside a fixed-size `Box` so the
ring appearing never nudges the skip buttons. Re-verified: `state=6` (buffering) under
`network speed gsm` / `delay gprs`, gold ring clearly sweeping around the button, controls
stationary. Review would not have caught this; only rendering it did.

## Not verified

**A retryable playback error could not be forced on this emulator.** What *was* observed live:
tapping a genre whose songs do not resolve raised the real `CarErrorOverlay` ("Nothing to Play")
above the shell, with **Dismiss only and no Retry** — the non-retryable gating working end to
end. Forcing the retryable branch (`onPlaybackError` → `isRetryable = true`) needs an actual
`PlaybackException`, and three attempts failed to produce one: throttling to `gsm`/`gprs` only
made ExoPlayer buffer, `speed 1:1` with a 4s delay likewise, and `iptables`/`ip6tables` REJECT on
80/443 did not stop playback or artwork loading on this image. Retry visibility for that branch
therefore rests on the Task 4 code review (`CarErrorOverlay.kt:166` gates on `error.isRetryable`)
and the unit-tested state, not on a device observation. Worth retrying on a device where the
audio host can actually be blocked.

**Process death returns an empty full player.** *(Fixed by T3 on 2026-08-25 — see
`docs/AAOS_T3_VERIFICATION.md`. The paragraph below records what A5 observed and is left as
written.)* After `am kill --user 10` (PID 6984 → 7789,
verified changed, playback stopped and app backgrounded first, per the A4 notes), relaunch
brought back the full player overlay with no crash and no `FATAL EXCEPTION` — but with no track,
`0:00 / 0:00`, and `dumpsys media_session` reporting `size=0`. Cause: `:automotive` has **no
restore wiring at all** — `PlaybackStatePersistence` is consumed only by the mobile
`PlayerViewModel.restorePlaybackState()`; neither `AutomotivePlayerViewModel` nor
`AutomotiveApp` mentions restore. This is pre-existing and untouched by A5, but it means the
car's answer to process death is an empty player rather than the queue the driver left playing.
Recorded here rather than fixed inside A5: wiring restore into the car ViewModel is a player
change, not an overlay change.

## Observations

- Tapping the "Afropop" genre still raises "Nothing to Play" — the genre resolves to zero songs.
  Same data-side gap A3/A4 recorded, not an A5 regression.
- Row keys are `song.mediaId`; a queue containing the same song twice would collide. Not hit in
  this run (139 distinct songs) and not introduced by A5.
