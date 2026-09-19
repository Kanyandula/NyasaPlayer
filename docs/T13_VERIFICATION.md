# T13 — verification record

Covers Task 5 of `docs/superpowers/plans/2026-08-26-aaos-t12-t13-playback-cleanups.md`. T13 moved
every transport action behind `PlayerTransport`, and its success paths have no automated coverage by
construction, so the device pass is the verification.

- **Date:** 2026-08-26
- **Branch:** `ek/aaos-t12-command-senders`

## Gates

289 tests, zero failures (`:core:playback` 55, `:automotive` 171, `:core:data` 63), detekt clean with
`detekt-baseline.xml` untouched and no `@Suppress` added, `lintOemDebug` at zero errors, both
automotive flavors and `:app:assembleDebug`. All re-run with `--rerun-tasks`.

`PlayerTransport` sits at 14 functions against `thresholdInClasses: 16` — the headroom D-T13.2
predicted, and the reason transport is its own class rather than eight more methods on the collector.

## Car — `AAOS_AOSP_33_userdebug`, driver user 10

Health at the start: 423 MB available, load 1.14. Restore fired on launch before any of this —
queue 8, item 4, paused — so T10 still holds on this build.

Every reading is from the app's own block in `dumpsys media_session`, anchored on its current pid.

| Operation | Evidence |
|---|---|
| `play` | `state=2 → 3`, position advancing 4198 → 6815 |
| `pause` | `state=3 → 2` at 9335 |
| `skipNext` | `item=4 → 5`, and 4 → 7 across three taps |
| `skipPrevious` | `item=5 → 4` |
| `seekTo` | `pos=9335 → 157656` |
| `toggleShuffle` | `item=7 → 0`, queue still 8 — the service moves the current track to index 0, which is `PlaybackQueueManager.toggleShuffle`'s contract |
| `skipToQueueItem` | tapped queue row 3 → `item=2`, "Lamb of God", and `state=3`: the moved body calls `play()` after seeking |
| `removeFromQueue` | queue `8 → 7`, current item untouched at `item=2` |
| `clearQueue` | queue `7 → 1`, header reads "End of queue", and the Clear Queue button dims — the `count <= 1` guard refusing a second press |
| `toggleRepeatMode` | set to All (icon gold); **the wrap branch was not observed** — see below |

Nine of ten operations behave exactly as they did before the move.

## Not verified

- **`skipNext`'s repeat-all wrap.** Repeat was set to All and the queue had been cleared to a single
  item, which is the state where `hasNextMediaItem()` is false and the `mediaItemCount > 0` guard
  decides whether the track restarts. The run stopped before that tap. It is the one line hand-
  carried into `PlayerTransport` with a comment, and its failure side is unit-covered; its success
  side is not.
- **The driving-state refusal.** Queue edits are gated in `CarQueueScreen`, not in the ViewModel or
  the transport — T13 changed nothing on that path — so this checks a contract this ticket does not
  touch. Worth doing on the next driving-state run regardless.
- **Mobile, entirely.** Same transport set plus `dismiss()` and the offline-buffering pause, both of
  which now route through the shared transport. Mobile also still carries T3's D55 index fix and
  T10's restore pass unverified on a device; one session with a signed-in phone closes all three.

## Observations

- The remove-from-queue confirmation sheet dismissed once without being confirmed, and the queue
  stayed at 8 — worth knowing that a missing tap looks identical to a refused operation from
  `dumpsys` alone. The second attempt confirmed explicitly and the queue dropped to 7.
- `CarQueueScreen`'s header counts *upcoming* songs, not the queue: it read "5 songs" while the
  session reported 8, because the current track sat at index 2. Not a defect, but it makes the
  header useless as a check on queue size — read the session instead.

## Mobile — run 2026-09-19

`Pixel_9_Pro_Fold_API_35` (`emulator-5558`, API 35), signed in, debug build of `:app` at `a0214b1`.
Two processes across the sitting — 2165 for the transport table, 6125 after the T10 force-stop for
everything below it. States read from `dumpsys media_session`, the UI from screenshots.

| Operation | Result |
|---|---|
| `play` | `state=PLAYING`, position 88111, "Vanity Remix" |
| `pause` | `state=PAUSED` at 90551 |
| `play` | `state=PLAYING` at 93596 |
| `skipNext` | `position=3708`, "Here We Stand" |
| `skipPrevious` | back to "Vanity Remix" |
| `seekTo` (75% of 4:29) | `position=201508` |
| `toggleShuffle` | icon purple, playback uninterrupted |
| `toggleRepeatMode` | icon purple, playback uninterrupted |
| `toggleLike` | the session's custom action flipped `Like → Unlike`, heart filled |
| `dismiss` | `state=NONE(0)`, `position=0`; the foreground service left the foreground, the media notification went, and the mini player disappeared from the UI |

That closes the transport set on mobile, `dismiss()` included.

### The offline-buffering pause

Airplane mode on mid-stream, then `svc wifi disable` / `svc data disable` to be certain — a ping
after each returned `Network is unreachable`, and `dumpsys connectivity` read
`Active default network: none`. Playback ran on to the end of what was already
buffered, 132350 ms, and then:

- the session went to `state=ERROR(7), error=Source error`, not a buffering spinner that never
  resolves;
- pressing play offline put the error in front of the user in words:
  **"Offline — Can't stream while offline. Download songs for offline playback."** with a Retry
  button, in the expanded player, over `OfflineBanner`'s "No internet connection";
- with the network back, play resumed from 2:17 and buffered ahead normally.

### Observation, not reproduced

Across that offline window the heart on "Vanity Remix" showed filled, and after reconnect it showed
unfilled, with the session's custom action back at `Like` — a like made while the Firestore backend
was unreachable looks like it did not survive the reconnect. The like had been tapped minutes
earlier, during a window where Firestore was logging `Could not reach Cloud Firestore backend`, and
no deliberate attempt was made to reproduce it. `toggleLike` is specified to roll back optimistically
and show a Snackbar on failure, which would explain it exactly; it is parked in `docs/BACKLOG.md`
rather than claimed as a defect.
