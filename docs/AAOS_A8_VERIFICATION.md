# AAOS Slice A8 — verification record

Records the device pass required by `docs/superpowers/specs/2026-09-13-aaos-a8-playback-states-design.md`
(Testing) and Task 6 of `docs/superpowers/plans/2026-09-13-aaos-a8-playback-states.md`.

- **Date:** 2026-09-14
- **Branch:** `ek/aaos-a8-spec`, final pass at `c651fde`
- **AVD:** `AAOS_AOSP_33_userdebug` (API 33, `userdebug`), 1024x768 at 160 dpi, one emulator only
- **Build:** `oem` debug APK, installed for user 10 (the driver)
- **Account:** the real signed-in user, against live Firestore, with a restored session from an earlier run
- **Evidence:** screenshots and `dumpsys` output from the session are described below; they are not
  committed, matching the earlier records

## Gates

| Command | Result |
|---|---|
| `./gradlew test detekt :automotive:lintOemDebug :app:assembleDebug :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug` | Pass — 684 tests, 0 failures |
| `git diff --stat main...HEAD -- app/` | Empty — `:app` untouched |

One run of the combined gate failed once at `:automotive:lintAnalyzeOemDebugUnitTest`; the next two
runs (lint alone, then the full gate) passed and it did not recur. The cause was not investigated.

## Going offline without taking the car stack down

`svc wifi disable` / `svc data disable` crashed `car_service`, `CarLauncher` and `audioserver` on this
emulator in an earlier slice, so they were not used. `adb shell cmd connectivity airplane-mode enable`
was, with the stack's PIDs compared before and after:

| Process | Before | After |
|---|---|---|
| `audioserver` | 13519 | 13519 |
| `com.android.car` | 13799, 14925 | 13799, 14925 |
| `com.android.car.carlauncher` | 15576 | 15576 |

`dumpsys connectivity` then reported `Active default network: none`. `airplane-mode disable` restored it.

## Checklist

| # | Check | Result |
|---|---|---|
| 4.1 | Offline, tap a song | **Pass.** "No Connection" overlay within 1.5 s, Wi-Fi-off icon, **Dismiss only**. The full player did not open; `dumpsys media_session` still showed the previous item, so nothing was queued. |
| 4.2 | Offline, shuffle a list | **Pass** (Favourites › Liked Songs › Shuffle): same as 4.1. |
| 4.3 | Network lost mid-track, then back | **Pass on outcome.** Audio kept playing from the buffer for ~125 s, then the overlay appeared **with Retry**. Online, Retry resumed at 6:22. See "Which path raised the mid-track overlay". |
| 4.4 | Offline relaunch onto a restored session | **Failed, fixed, re-run: pass.** First run raised the overlay with no input — see "Fixed during this pass". On `c651fde`: ExoPlayer logged the restore's load error, no overlay appeared, the banner showed, the session sat paused. |
| 4.4 | …then press play offline | **Pass.** Overlay **with Retry**, no Skip next (offline). Online, Retry re-prepared the errored player and played (`media_session` state 3). |
| 5 | Non-network error with a next track | **Pass.** One cached song's URL pointed at a 404 in Room, online: overlay with Retry **and** Skip next; Skip next played the next track (state 3). The layout finding below came from this step. |
| 5 | Non-playback error | **Pass.** A genre with no songs offline raised "Nothing to Play" with **Dismiss only**. |
| 5 | One-song queue, repeat-all → no Skip next | **Not run.** Search could not be driven through `adb` after two attempts. The gate (`hasNext && queueSize > 1`) was checked in Task 4's review. |
| 6 | Driving: overlay still works | **Pass.** `Current Driving State: 2`, `DO: true UxR: 255`. The overlay was not evicted; Retry re-raised it (bad URL), Skip next played the next track, and — offline, via the play guard — Dismiss cleared it. |
| 7 | Contrast | White label on card `#181824` **17.6:1**; dark label on Retry's gold `#C9A84C` **8.7:1**; outlined-pill border `#34343F` **1.4:1** against the card — see findings. |

The edited Room row was restored byte-for-byte from a backup afterwards; the car was returned to
PARK and airplane mode disabled.

## Which path raised the mid-track overlay

Not the buffering guard. For a progressive stream the loader fails for good while buffered audio plays
on, and ExoPlayer raises its error (`UnknownHostException (no network)`, logged at the moment the
buffer ran out) straight from READY — there is no buffering window for the guard to catch. The guard
covers the case where playback *starts* buffering offline; the overlay the driver sees mid-track comes
from `onPlaybackError`, with the same wording, icon and Retry.

## Fixed during this pass

- **4.4 — an offline restore raised the overlay unprompted** (`3ac6458`). The restore prepares its
  queue paused, the load fails, and `onPlaybackError` put "No Connection" in front of a driver who had
  pressed nothing — against T3's D-T3.5 and this slice's acceptance criterion. `onPlaybackError` now
  raises nothing while the snapshot says the player is not trying to play. This also stops a later
  ExoPlayer error re-raising the overlay after the offline guard paused the player.
- **5 — three pills wrapped mid-word** (`c651fde`). The half-width card leaves 416 dp; three pills got
  128 dp each, 56 dp of it for a 20 sp label, so "Dismiss" rendered as "Dismi / ss". With Skip next
  shown, Skip next and Retry now share a row and Dismiss takes its own. Re-checked on the device:
  every label on one line.

## Findings recorded, not fixed here

- **`NetworkMonitor` missed one live offline transition.** On the first airplane-mode toggle the app
  stayed "online" — no banner, `isOffline` false — while the device had no default network; a
  relaunch and a second toggle both worked. Suspected cause: it re-reads `activeNetwork` inside
  `NetworkCallback.onLost`, which the platform documents as possibly stale. It lives in `:core:common`,
  so fixing it changes mobile, which this slice may not (spec decision 4). **T29.** Until then, a miss
  falls back to the pre-A8 slow path.
- **A 404 is called "No Connection".** `onPlaybackError` treats `error.cause is IOException` as a
  network failure, and a bad response or an unparseable file is an `IOException` too.
- **The outlined `CarPillButton` border is 1.4:1** against the card, under WCAG 1.4.11's 3:1 for
  component boundaries. It predates A8 — the house outlined variant, and the overlay's old hand-rolled
  Dismiss box was an equally faint 10% white fill. The 17.6:1 label is what identifies these buttons.
- **Method:** `uiautomator dump` fails silently while playback animates the UI and leaves the previous
  dump in place. Screenshots and `dumpsys media_session` were used as the oracle after that.
