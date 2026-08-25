# AAOS T3 — verification record

Closes Task 5 of `docs/superpowers/plans/2026-08-25-aaos-t3-automotive-playback-restore.md`:
the automotive custom launcher restores the session it was killed with, instead of the empty
player `docs/AAOS_A5_VERIFICATION.md` recorded.

- **Date:** 2026-08-25
- **Branch:** `ek/aaos-t3-automotive-playback-restore`
- **AVD:** `AAOS_AOSP_33_userdebug` (API 33, `userdebug`), one emulator only
- **Build:** `oem` debug APK, installed for user 10 (the driver)
- **Account:** the real signed-in user, against live Firestore — not a fake
- **Health before the run:** `MemAvailable` 662 MB of ~2 GB, load `0.34` — above the ~200 MB
  floor where ANR and perf findings stop being credible

## Gates

| Command | Result |
|---|---|
| `./gradlew :core:playback:testDebugUnitTest` | Pass — 34 tests |
| `./gradlew :automotive:testOemDebugUnitTest` | Pass — 171 tests |
| `./gradlew :core:data:testDebugUnitTest` | Pass — 63 tests |
| `./gradlew detekt` | Pass — `detekt-baseline.xml` untouched |
| `./gradlew :automotive:lintOemDebug` | Pass — 0 errors, 4 pre-existing warnings, none in a T3 file |
| `assembleOemDebug` / `assemblePlaystoreDebug` / `:app:assembleDebug` | Pass |

All three test tasks were re-run with `--rerun-tasks`; 268 tests, zero failures. The
`FavouritesBoundaryTest` / `FavouritesJourneyTest` compile blocker A5 recorded is gone — the
`:automotive` suite runs with nothing moved aside.

## The process-death run

Ground truth before the kill, taken from the full player and `dumpsys media_session`:

| | Before kill | After relaunch |
|---|---|---|
| Track | AMAZING SAD GUITAR RAP INSTRUMENTAL… | **same** |
| Queue index | `active item id=2` of 8 | **`active item id=2` of 8** |
| Position | `position=59324` (0:59 of 3:12) | **`position=59327`** |
| Repeat | All (gold icon) | **All** |
| Like | filled heart / `custom actions=[Unlike]` | **`[Unlike]`** |
| Playback | paused (`state=2`) | **paused (`state=2`)** — did not start itself |
| Queue in Media3 | `queueTitle=null, size=8` | **`size=8`** |
| Owning process | `ownerPid=2485` | `ownerPid=2721` |

`am kill --user 10` after backgrounding, PID **2485 → 2721**, verified changed rather than
merely present. Position came back 3 ms off because the pause itself triggers a save; D-T3.9's
30-second staleness bound applies to a kill *during* playback, not to this path.

The relaunched shell also came back on the full player — `rememberSaveable` restoring the T6
overlay stack — so this is exactly the screen A5 photographed empty, now populated.

Restore also fired on the very first launch after a fresh install, before any of the above:
`size=13`, `position=222040`, paused. Nothing about it depends on the app having run this boot.

### Re-verified after review

The three reviews changed `restore()` again — `RepeatMode.entries.firstOrNull` for the mode, one
redundant guard removed — so the run was repeated on the shipped build: same track, `active item
id=2`, `position=59327`, paused, `size=8`. Restore landed later than in the earlier runs, about a
minute after a fresh install rather than 10–20s, because Firestore reconnects more slowly after a
reinstall. The like state also arrives a beat behind the queue: the session read `Like` at first
and `Unlike` seconds later, which is the like flow emitting after the restore paints, not a
regression.

## The OEM template surface

With the launcher's restore already applied, `com.android.car.media` shows the restored track in
its own mini player, paused, with the progress bar part-filled. The template sees the restored
session because it shares the one `MediaLibrarySession` — **not** because it restores anything
itself. D-T3.1 stands: a driver who only ever opens the template still gets nothing back.

## Not verified

- **The pre-send emptiness re-check (D56) — now verified, see below.**

- **The missing-state case.** Deleting the user's `users/{uid}/playbackState/current` document is
  the way to reach it, and both routes to that — the Firestore MCP and `run-as --user 10` — were
  refused by this session's permission classifier. Left unrun rather than worked around. The
  branch itself is covered by `PlaybackStatePersistenceTest`, which asserts a `null` return for a
  missing document, a blank song id and unresolvable ids; what is missing is the device-level
  claim that a `null` restore leaves an empty player with no crash and no error overlay.

## The D56 race, verified by mutation

Reaching this needed the window held open: two instrumented builds carrying a `delay()` after
`restore()` returned, one with the guard and one without, both reverted afterwards. The tree matched
`HEAD` and the shipped build was reinstalled and re-checked before this record was written.

**With the guard.** App launched, restore parked in the probe, "Play" tapped on the resume card
once Home had content. Mona Lisa started (`size=8`, `active item id=0`, `state=6` buffering). When
the window closed: **`state=3`, position 48666, still Mona Lisa**. The restore saw a non-empty
player and returned.

**Without the guard.** Identical run. Mona Lisa started the same way — and when the window closed
the session read **`state=2`, position 60803**. Playback had been paused and re-seeked to the saved
position: `handleRestoreState` had applied its queue and its `playWhenReady = false` on top of a
track the driver had started seconds earlier. The saved song happened to be Mona Lisa too, so the
title did not change, but a live track cannot jump from a few seconds in to 60803 ms and stop
playing by itself. That is the regression D56 exists to prevent, reproduced.

## A false start worth recording

The first two attempts used the OEM template as the racer — launch our shell, switch to the
template, tap a row there. Both showed the started track surviving, and both proved nothing:
putting another app in front stops our shell composing, so `AuthenticatedApp` never reaches the
player ViewModel and the restore never runs. The guard was not what saved playback in those runs;
nothing had threatened it. It only became a real test once the racer was a tap **inside our own
foreground app**, which is also the realistic case — a driver relaunching the app and immediately
hitting Play.

I recorded "the guard held" after the first of those runs. It did not hold anything; there was
nothing to hold.

## Observations

- `dumpsys media_session` lists more than one session; anchor every read on the block whose
  `ownerPid` matches the app's *current* pid. A `grep -A9 ownerPid=…` window is also too short —
  `queueTitle` sits eleven lines below it, and cutting it off looks exactly like a queue of zero.
- The app process restarts on its own within seconds of `am kill`, so it was a new pid each time,
  not a refused kill.
