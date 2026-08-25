# T10 — verification record

Closes Task 6 of `docs/superpowers/plans/2026-08-25-aaos-t10-collector-restore.md`. T10 moved
restore policy into `BasePlayerStateCollector`, so both surfaces need a pass — this is the first
ticket in the series where mobile is not out of scope.

- **Date:** 2026-08-25
- **Branch:** `ek/aaos-t10-collector-restore`

## Gates

275 tests, zero failures (`:core:playback` 41, `:automotive` 171, `:core:data` 63), detekt clean
with `detekt-baseline.xml` untouched, lint clean, both automotive flavors and `:app:assembleDebug`
building. All re-run with `--rerun-tasks`.

`grep -rn "restored\." app/src/main automotive/src/main` returns exactly two lines, one per surface,
both `observeCurrentSongLikeState(restored.song.mediaId)`.

## Car — `AAOS_AOSP_33_userdebug`, driver user 10

Health before the run: 596 MB available, load 0.87. The T3 process-death protocol, against a build
where the policy now lives in the collector.

| | Before kill | After relaunch |
|---|---|---|
| Track | AMAZING SAD GUITAR RAP INSTRUMENTAL… | **same** |
| Queue index | `active item id=2` of 8 | **`active item id=2` of 8** |
| Position | `34701` (0:34 of 3:12) | **`34711`** |
| Repeat | All — gold icon | **All** |
| Like | filled heart / `[Unlike]` | **`[Unlike]`** |
| Playback | paused (`state=2`) | **paused** |
| Owning process | `ownerPid=12456` | `ownerPid=12766` |

`am kill --user 10` after backgrounding; PID changed, verified rather than assumed. The relaunched
full player is pixel-identical to the screenshot taken before the kill, including the 3:12 duration
and the gold repeat icon — the two fields the shared `applyRestored` now computes from the restored
value rather than from the controller.

Restore also fired on the first launch after installing the new build, before any of the above.

## Mobile — `Medium_Phone_API_35`, not established

The phone AVD is signed in and the app launches with content, so this should have been the easy
half. It was not, and the evidence does not line up well enough to claim anything.

What was seen, in order:

1. After installing the T10 build and launching, Home rendered signed-in and a mini player appeared
   carrying the same session the car had left — the AMAZING SAD GUITAR track, paused, progress bar
   part-filled. On its face that is a successful restore, and under T10 the mini player only rises
   on a non-null result.
2. `dumpsys media_session` lists **no session for `com.example.nyasaplayer`** — 91 lines, telecom
   and Bluetooth only. Without a session there is no `MediaController`, and `restoreIfIdle` returns
   null at its first line, which contradicts (1).
3. A tap intended to expand the mini player left the expanded player showing a *different* track
   in a playing state, with still no session anywhere and nothing from the app in logcat.

One reading fits all three — the service never came up on this AVD, so the UI is showing optimistic
state with no player behind it — but that is inference, and (1) argues against it. Rather than
keep poking at it past midnight, it is recorded as **not established**.

The car pass above stands on its own: same collector, same `applyRestored`, same `restoreIfIdle`,
and there the session evidence is unambiguous.

**To finish:** a session that starts from a clean phone AVD, watches `PlaybackService` start (or
fail) in logcat from the first launch, and only then judges restore. Worth checking whether the
service reaches foreground at all on API 35 with this AVD's notification permissions — a
`startForegroundService` refusal would explain every line above.

Mobile therefore still carries what it carried before T10: unverified restore, and T3's D55 index
fix shipped without a device pass.
