# T28 - Mobile keeps its own offline checks, and may raise an error on an offline restore

- **Slice:** mobile follow-up to A8
- **Depends on:** A8 — `Song.isPlayableNow` and `PlaybackSnapshot.playWhenReady` in `:core:playback`
- **Status:** Filed, not specced
- **Verification Command:** `./gradlew :app:testDebugUnitTest :app:assembleDebug`, plus a phone pass
- **Design Reference:** `docs/superpowers/specs/2026-09-13-aaos-a8-playback-states-design.md`; D71
- **Risk Tags:** mobile regression, no unit test net, offline playback
- **Affected Modules:** `:app`

## Problem

A8 gives the car a shared offline rule, `Song.isPlayableNow(isOnline)`, and deliberately leaves `:app`
alone. So mobile still has its own copy of the same idea, written three times in `PlayerViewModel`
as `!isOnline && !isDownloaded`, and the two surfaces can drift.

Mobile also probably has a bug the car's version was designed around. `handleOfflineBuffering()`
pauses and raises "Connection lost" whenever the snapshot says `isBuffering` while offline. But a
restore buffers too: `PlaybackService` runs `applyQueueToPlayer()`, which calls `prepare()`, before it
sets `playWhenReady = false`. So an offline mobile restore likely shows an error to someone who has
pressed nothing. **Not device-verified**; the mechanism is read from the code, and the Codex review of
the A8 spec found the same trigger on the car side.

## Scope

- `playSong`: check `resolveSongUri(song).isPlayableNow(isOnline)`.
- `shufflePlay`: `resolvedSongs.any { it.isPlayableNow(isOnline) }` replaces the `zip` comparison.
- `handleOfflineBuffering()`: add `snapshot.playWhenReady` to the condition, so buffering without a
  play attempt raises nothing. Reproduce the restore error on a phone **first**; if it does not
  happen, say why in the Outcome and keep the change only if it is still correct.
- Keep the wording mobile uses today.

## Out Of Scope

- **`togglePlayPause`.** It must keep `downloadManager.getLocalFileUri(currentMediaId)`. Restored songs
  reach the snapshot through `applyRestored` without `resolveSongUri()`, so a restored song that is
  downloaded still carries its `https:` URL; `isPlayableNow` would refuse what today's check allows.
  Fixing that belongs to A9, which moves local-URI resolution into shared code, restore included.
- Surface checks such as `isMobileApp` in shared code. Surfaces differ by module (D71).
- A `PlayerViewModel` unit-test harness. Worth having; not this ticket.

## Acceptance Criteria

- Given the phone is offline, when a streamed song is tapped, then playback is refused with today's
  wording.
- Given the phone is offline, when a downloaded song is tapped, then it plays.
- Given the phone is offline, when a list mixing both is shuffled, then playback starts.
- Given the phone is offline, when the app relaunches onto a restored session, then no error is shown
  until play is pressed.

## Notes

`PlayerViewModel` has no unit tests; `:app`'s only test is `SongMediaItemMapperTest`. Every acceptance
criterion above is a phone check. The T14 mobile pass that `docs/T14_VERIFICATION.md` still owes needs
the same device and the same back-out-and-return gestures, so the two are worth running together.
