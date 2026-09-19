# T28 - Mobile keeps its own offline checks, and may raise an error on an offline restore

- **Slice:** mobile follow-up to A8
- **Depends on:** A8 — `Song.isPlayableNow` and `PlaybackSnapshot.playWhenReady` in `:core:playback`
- **Status:** Done — specced, implemented and device-verified; see Outcome
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
criterion above is a phone check. The T14 mobile pass (see `docs/T14_VERIFICATION.md`) needs the same
device and the same back-out-and-return gestures, so the two are worth running together, along with
T29's owed phone pass — `scripts/aaos-network-toggle-check.sh` with `NT_USER=0 NT_LAUNCH="monkey -p
com.example.nyasaplayer -c android.intent.category.LAUNCHER 1"`, plus its offline checks (see
`docs/T29_VERIFICATION.md`).

## Outcome

Specced in `docs/superpowers/specs/2026-09-19-t28-mobile-offline-rule-design.md`, implemented and
verified on `Pixel_9_Pro_Fold_API_35` on 2026-09-19.

**Four call sites, not three.** `togglePlayPause` came back into scope: the reason this ticket held
it back — restored songs carrying `https:` URLs — was retired by A9, which moved resolution into
`PlaybackStatePersistence.restore()`. Its `!isOnline` gate stays explicit, because
`isPlayableNow` on a null song refuses whatever the network is doing, and the tempting one-liner
would have turned an online tap with no current song into an offline error.

**The predicted restore bug is not real on this build.** Probed before any code changed: offline,
`am force-stop`, relaunch — restore completed from Firestore's cache, the session sat at
`ERROR(7)` at the saved position, and the UI showed no error in either the mini or the expanded
player. The `playWhenReady` clause still went in, as the car's rule, not as a fix.

**A different gap was real.** Mobile's buffering guard had no local-file exemption, so a downloaded
song that buffered offline was paused and told "Connection lost". `isStreamStalledOffline` exempts
local files, and criterion 5 below covers it.

`shufflePlay`'s old `zip` comparison also misread two classes of song — one whose catalogue
`audioUrl` is already the local URI, and one whose `audioUrl` is blank with a `file:` `songUrl`.
Both are unit-covered now.

### Device pass, all offline unless stated

| Criterion | Result |
|---|---|
| 1 — streamed song tapped | Refused: "Offline / This song isn't available offline" with Retry; no queue change |
| 2 — downloaded song tapped | Plays: `PLAYING`, position advancing, buffered from the local file (`files/downloads/56.audio`) |
| 3 — mixed list shuffled | Allowed: the queue was set and a track loaded. Shuffle happened to land on a streamed song first, which then failed with the standard offline error — the gate decides the start, not what the player can then load |
| 4 — relaunch onto a restored session | Restored, paused, **no error** until play is pressed |
| 5 — seek inside a downloaded song | Playback continued through the seek with no pause and no "Connection lost" |

Criterion 5 shows no spurious error; it does not prove the player entered `STATE_BUFFERING` during
that seek, so it is evidence the guard stays quiet rather than proof the exempting branch ran.

### Found on the way, not fixed here

`PlaybackService.onAddMediaItems` queues downloaded songs unresolved, so a song requested by
Assistant, Bluetooth or system media resumption ignores its download — filed as
`docs/tickets/T31-add-media-items-ignores-downloads.md`, both surfaces, outside this ticket's
`:app` scope.

### Gates

`./gradlew test detekt :app:lintDebug` — BUILD SUCCESSFUL, **831 tests, 0 failures**, detekt clean,
lint clean. Nine of those tests are new (`OfflinePlaybackGateTest`); they prove the rule, not the
wiring, which is what the device pass above is for.
