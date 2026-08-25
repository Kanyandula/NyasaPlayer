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

## The OEM template surface

With the launcher's restore already applied, `com.android.car.media` shows the restored track in
its own mini player, paused, with the progress bar part-filled. The template sees the restored
session because it shares the one `MediaLibrarySession` — **not** because it restores anything
itself. D-T3.1 stands: a driver who only ever opens the template still gets nothing back.

## Not verified

- **The pre-send emptiness re-check (D-T3.7 rule 1).** Four attempts, none reached the state
  worth testing. The window between `restore()` returning and the send is well under a second in
  practice, and the emulator needs 15–30 s after a relaunch before Home has content to tap — the
  window closes long before a tap can land. Throttling the network (`emu network speed gsm`,
  `delay gprs`) did not widen it: the playback-state document comes from Firestore's local cache.
  Two instrumented builds carrying a 20 s and then a 60 s `delay()` after the read did hold the
  window open — and confirmed restore still lands correctly behind a delay — but Home was still
  on its loading skeleton for most of it, and every tap issued into that window was swallowed.
  Both probes were reverted; the tree matched `HEAD` before the final run, and the final
  verification above was taken on the shipped build.

  So the guard's *presence* is a code-inspection fact, not a device observation. What the probes
  did show is that the race is narrow: restore consistently landed within 10–20 s of launch,
  usually far less, and always before a driver could realistically start something else.

- **The missing-state case.** Deleting the user's `users/{uid}/playbackState/current` document is
  the way to reach it, and both routes to that — the Firestore MCP and `run-as --user 10` — were
  refused by this session's permission classifier. Left unrun rather than worked around. The
  branch itself is covered by `PlaybackStatePersistenceTest`, which asserts a `null` return for a
  missing document, a blank song id and unresolvable ids; what is missing is the device-level
  claim that a `null` restore leaves an empty player with no crash and no error overlay.

## Observations

- `dumpsys media_session` lists more than one session; anchor every read on the block whose
  `ownerPid` matches the app's *current* pid. A `grep -A9 ownerPid=…` window is also too short —
  `queueTitle` sits eleven lines below it, and cutting it off looks exactly like a queue of zero.
- The app process restarts on its own within seconds of `am kill`, so it was a new pid each time,
  not a refused kill.
