# T14 — verification record

Covers the device pass for `docs/superpowers/plans/2026-08-26-aaos-t14-reconnection.md`.

- **Date:** 2026-08-26
- **Branch:** `ek/aaos-t14-reconnection`
- **AVD:** `AAOS_AOSP_33_userdebug`, driver user 10. Health at the start: 543 MB available, load 0.48.

## Gates

**315 tests, zero failures** (`:core:playback` 81, `:automotive` 171, `:core:data` 63), detekt clean
with `detekt-baseline.xml` untouched and no `@Suppress` added, `lintOemDebug` at zero errors, both
automotive flavors and `:app:assembleDebug`.

## The failure mode is proved in tests, not on this device

`SharedControllerFutureTest` and `ControllerConnectionTest` prove the bug and the fix on the JVM: a
released shared future hands the same disconnected controller to the next consumer, and reference
counting plus `reconnect()` fixes it. That is where the evidence lives.

**The car could not reproduce it by hand.** The trigger is a ViewModel reaching `onCleared()` while
the process lives, and on this AAOS build backing out of `AutomotiveActivity` does not finish it —
`dumpsys activity activities` still lists the record, and playback keeps working. Running the
**pre-fix `main` build** through back-out and relaunch (same pid, 8826) left play working, so the
gesture never triggered the condition.

That is worth stating plainly: **the car half of this pass is a regression check, not a
reproduction.** The surface where an Activity finish is routine — mobile, where back from the root
activity finishes — is the one that would show it, and that pass is still owed.

## Regression check on the fix, car

Same process throughout, pid 9119 — so every result is about the controller, not a restart.

| Step | Result |
|---|---|
| Launch | Restore fired: queue 8, `active item id=2`, paused at 161585 |
| Play | `state=2 → 3` |
| Pause | `state=3 → 2`, position advanced to 166134 |
| Next | moved to the following track, `position=0` |
| Back out ×3, relaunch, play | `state=3`, position advancing — pid unchanged at 9119 |

Nothing regressed: the connection change is invisible on a healthy player, which is what Principle 5
of the plan asked for.

## Still owed

- **Mobile**, where the failure mode is reachable: play, finish the Activity by backing out of the
  root, return, press play. Broken on `main`, expected to work on this branch. Needs a signed-in
  phone — the same sitting that owes T3's D55 check, T10's restore pass, T13's transport pass and
  T7's skeletons.
- **A driving-state pass** on the car, to confirm no new surface appears mid-drive. T14 adds no UI,
  so this is a formality rather than a risk.

## Mobile — run 2026-09-19, the failure mode's own surface

`Pixel_9_Pro_Fold_API_35` (`emulator-5558`, API 35), signed in, debug build of `:app` at `a0214b1`.
Same process throughout (6125), so every result is about the controller rather than a restart.

Both ways of finishing the Activity were tried:

| Step | Result |
|---|---|
| Playing, swipe the task off the recents list | Process survives, `PlaybackService` still foreground, playback runs on (position 150009) |
| Relaunch | Controller rebuilt: mini player live on the same track, position advanced to 173237, now-playing marks back on the Home tiles |
| Playing, back out of the root activity (launcher resumed, `NexusLauncherActivity` on top) | Playback continues, position 15128 |
| Return, press pause | `PLAYING → PAUSED` at 38444 |
| Press play | `PAUSED → PLAYING` at 41494 |

The last two rows are the check this record has been owing: on pre-fix `main` a released shared
future hands the next consumer a disconnected controller, so play after an Activity finish does
nothing. Here both transport presses land on the first try.

The driving-state pass on the car is still a formality this ticket has not run.
