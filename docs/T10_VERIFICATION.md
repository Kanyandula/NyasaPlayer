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
