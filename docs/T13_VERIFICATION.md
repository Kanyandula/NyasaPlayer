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
