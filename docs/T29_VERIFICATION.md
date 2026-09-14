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

The review of `be3943e` found a startup window of microseconds: on API 31+ `onAvailable` and
`onCapabilitiesChanged` arrive in one handler message but take the lock separately, so a seed landing
between them is ignored and `isOnline` reads `false` until capabilities publish. That is the spec's
own rule (a callback outranks the seed) and it corrects itself; worth remembering if a startup banner
flicker is ever seen.
