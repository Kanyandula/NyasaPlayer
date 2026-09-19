# T29 — verification record

Covers the device pass for `docs/superpowers/plans/2026-09-14-t29-network-monitor.md` (Task 4).

- **Date:** 2026-09-14
- **Branch:** `ek/t29-network-monitor`, fix at `be3943e`
- **Car AVD:** `AAOS_AOSP_33_userdebug`, driver user 10, `oem` debug, signed in, playback stopped
- **Script:** `scripts/aaos-network-toggle-check.sh` — ten `cmd connectivity airplane-mode` round
  trips; each transition waits for `dumpsys connectivity` to reach the new state, settles 5 s, then
  reads system → banner → system, and counts only when both system reads agree

## Gates

`./gradlew test detekt :automotive:lintOemDebug :app:assembleDebug :automotive:assembleOemDebug` —
green, **734 tests, 0 failures** (the 14 `DefaultNetworkStateTest` cases run in both `:core:common`
variants). No file under `app/` or `automotive/` changed: `NetworkMonitor`'s public surface is the same.

## The car

| Build | Result |
|---|---|
| `main` (before) | **1 miss / 20 valid transitions** — an offline transition where the system settled offline and the app stayed "online" |
| `be3943e` (fix) | **0 misses / 20 valid transitions** |

The APK tested was checked to contain `DefaultNetworkState` before installing.

Twenty transitions is a small sample, and one miss in twenty does not make zero in twenty proof on its
own. The fix rests on the platform's documented contract — `NetworkCallback.onAvailable` says not to
call synchronous `ConnectivityManager` methods inside callbacks because their results may not be
current — and this run is consistent with it.

A first baseline attempt used a fixed 10 s wait; this emulator takes 15–25 s to reconnect, so every
back-online check read a system that had not reconnected. It was discarded and the script rewritten
to wait for the system state before checking.

## The phone — not run, owed

`Medium_Phone_API_35` could not take the build:

- It first came up from its snapshot with a guest load average of ~72, and adb system queries
  (`cmd connectivity airplane-mode`, `dumpsys package`) hung until it settled, about 40 minutes later.
- It then shut down cleanly on its own (snapshot saved) and came back calm.
- `adb install -r` failed: `java.io.IOException: Requested internal only, but not enough space`.
  `/data` was 95% full (312 MB free), at Android's low-storage threshold. `pm trim-caches` freed
  nothing, so the space is app data and installed apps, not caches.

Owed: the same script with `NT_USER=0 NT_LAUNCH="monkey -p com.example.nyasaplayer -c
android.intent.category.LAUNCHER 1"`, and by hand — offline, a streamed song refused with mobile's
wording; downloads refuse to start offline; online again, the banner clears and a song plays. It needs
the same device as the T28 and T14 mobile passes.

## Also noted

The review of `be3943e` found a startup window of microseconds: from API 26 `onCapabilitiesChanged`
always follows `onAvailable` immediately, but each takes the lock separately, so a seed landing
between them is ignored and `isOnline` reads `false` until capabilities publish. That is the spec's
own rule (a callback outranks the seed) and it corrects itself; worth remembering if a startup banner
flicker is ever seen.

## The phone — run 2026-09-19

`Pixel_9_Pro_Fold_API_35` (`emulator-5558`, API 35, `/data` 37% used) took the build that
`Medium_Phone_API_35` could not. Run by hand rather than through
`scripts/aaos-network-toggle-check.sh`: the same phone sitting was also closing T10, T13, T14 and
T25, and the checks below are the ones that script's `NT_LAUNCH` form was meant to reach.

| Check | Result |
|---|---|
| Offline (`cmd connectivity airplane-mode enable`, then `svc wifi disable` / `svc data disable`; `ping` → `Network is unreachable`) | `OfflineBanner` — "No internet connection" — on Home, Library and Downloads, within one screenshot of the toggle |
| A streamed song, offline | Refused in mobile's own wording: **"Offline — Can't stream while offline. Download songs for offline playback."** with Retry, in the expanded player |
| Playback already running when the network went, and recovery afterwards | Measured in the same sitting and written up once, in `docs/T13_VERIFICATION.md` → "The offline-buffering pause" |
| Profile, offline | Its own `ErrorBanner`: "Connection lost — Showing cached content", with Retry, over cached content |
| A download started offline | Nothing downloaded — `files/downloads` does not exist — but see below |

### Finding: a download refused offline says nothing

`SongDownloadManager` checks `networkMonitor.isOnline` and calls `downloadRepository.markFailed`
(`SongDownloadManager.kt:51`). On the phone that refusal is invisible: the overflow sheet closes as
if the download had started, no Snackbar appears in the next 8 s, and the Downloads screen still
reads "0 songs / No downloads yet" — not a failed row. The user is told nothing at all, on a screen
that is otherwise showing them the offline banner.

Parked in `docs/BACKLOG.md`; the car's equivalent refusal is A9's and was not re-checked here.
