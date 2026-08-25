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

- **The `:automotive` unit tests the ticket's fourth criterion asked for.** `MediaController` is
  `@DoNotMock` with a package-private constructor, so the ViewModel's controller path cannot be
  driven in a unit test. The restore branching is covered in `:core:playback` instead, and the
  ticket is amended to say so.

## The missing-state case

Run with the user's `users/{uid}/playbackState/current` deleted — approved, and confirmed to be the
right document first by matching its contents against the live session (queue of 8, `positionMs`
62649, `savedAt` 19:36). The app was force-stopped before the delete so the 30s save loop could not
rewrite it underneath the test.

Relaunched with nothing to restore:

| Check | Result |
|---|---|
| Media session | `metadata: null`, `queueTitle=null, size=0`, `state=0` |
| Mini player | Absent — no ghost row, Home sits flush to the system bar |
| Error overlay / dialog | None |
| Crash or ANR | None — no `FATAL EXCEPTION`, no `ANR in com.example.nyasaplayer` in logcat |
| Rest of the app | Working — Home, resume card and Continue Listening all populated |

The "Pick up where you left off" card still names a song, and that is correct: it reads recently
played, not playback state. It offers something to start, it does not claim a session is active,
which is the "misleading active-player state" the ticket's criterion is about.

Afterwards the app rewrote the document on the next play/pause — `savedAt` 19:40:22, the resume
point restored.

## Observations## Observations

- `dumpsys media_session` lists more than one session; anchor every read on the block whose
  `ownerPid` matches the app's *current* pid. A `grep -A9 ownerPid=…` window is also too short —
  `queueTitle` sits eleven lines below it, and cutting it off looks exactly like a queue of zero.
- The app process restarts on its own within seconds of `am kill`, so it was a new pid each time,
  not a refused kill.
