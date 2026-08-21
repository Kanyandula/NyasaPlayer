# AAOS Slice A6 — verification record

Records the §8.2 manual checklist and §8.3 gates required by
`docs/superpowers/specs/2026-08-20-aaos-search-design.md` §11.

- **Date:** 2026-08-21
- **Branch:** `ek/aaos-a6-t1-search-viewmodel`
- **AVD:** `AAOS_AOSP_33_userdebug` (API 33, `userdebug`), one emulator only
- **Build:** `oem` debug APK, installed for user 10 (the driver)
- **Account:** the real signed-in user, against live Firestore — not a fake
- **Library under test:** 41 songs match `worship` across title, artist and album
  (counted directly in Room, see "The album-search proof"), against a reported cap of 21
  (`Max Cumulative Content Items: 21`), so the truncation check below is not vacuous

## Gates

| Command | Result |
|---|---|
| `./gradlew :automotive:testOemDebugUnitTest` | Pass — 118 tests, 0 failures, 0 errors |
| `./gradlew :core:data:testDebugUnitTest` | Pass — 54 tests, 0 failures, 0 errors |
| `./gradlew detekt` | Pass — no new baseline entries |
| `./gradlew :automotive:lintOemDebug` | Pass |
| `./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug` | Pass — both flavors |

Unlike A5, the whole `:automotive` unit-test source set compiles with no files moved aside: the
Favourites suites A5 had to set aside are now the merged versions from PR #26 (plan Task 0).

## Manual checklist (§8.2)

| # | Check | Result |
|---|---|---|
| 1 | Parked: search icon enabled; settings/profile visibly disabled | Pass — search renders in `CarTextSecondary` and opens the sheet; settings and profile stay `CarTextDisabled` and inert |
| 2 | Parked: opening search focuses the field and the platform keyboard appears | **Partly** — the platform IME appears (`mInputShown=true`) with a Search action key, but search does **not** auto-focus on open. Deliberate, recorded as D40 |
| 3 | Parked: submitting a query shows loading and then results | Pass — IME Search action submitted `worship`; results rendered with top result and rows |
| 4 | Parked: tapping the top result and a later row starts playback from the visible result list | **Partly** — a later row passed: tapping row 2 gave `active item id=2` against `queueTitle=null, size=21`, i.e. the tapped song inside the visible list. The top-result card was not tapped successfully; it routes through the same `onSongClick(results, top)` call one line away |
| 5 | Parked: no-results and error states readable and recoverable | **Partly** — no-results passed: "No results / Nothing in your library matches …" with an "Edit search" CTA. A search **error** could not be forced on this image, same limitation A5 recorded for playback errors |
| 6 | Parked: recent queries de-duplicate and cap at five | **Not on device** — population verified (a submitted query appears as a recent chip and survives into the driving sheet). De-dupe and the cap of five are covered by unit tests, one of them mutation-checked |
| 7 | Driving at `DO: true UxR: 255`: search shows the voice prompt and no keyboard | Pass — field replaced by the non-clickable prompt, `mInputShown=false`, and the `Songs` shortcut is absent |
| 8 | Driving: results from a previously submitted query remain viewable and are capped | Pass — results stayed on screen with no eviction, and the queue handed to playback was 21 of the repository's 41 |
| 9 | Driving while actively editing: evicts from text entry and shows the reason | Pass — see "The eviction proof" |
| 10 | Manifest check: no variant requests `RECORD_AUDIO` | Pass — absent from every source manifest, from both merged manifests (`oemDebug`, `playstoreDebug`), and from `dumpsys package` on device |

Driving state was the real thing throughout: `inject-vhal-event 0x11400400 8` plus
`inject-continuous-events 0x11600207 40` gave `DO: true UxR: 255`, and returning the gear to
park gave `DO: false UxR: 0`.

## The eviction proof

With the vehicle parked, search was opened, the field focused (`mInputShown=true`) and `worship`
typed. Driving state was then injected underneath the active field.

Within one restriction callback the app closed the search sheet, dismissed the IME, returned to
the Home tab root, and raised `CarRestrictionDialog` reading:

> **Not available while driving**
> Typing is unavailable while driving. Use voice search instead.

That is `ReasonTextEntry` verbatim, so the gate denied on `sheet == CarSheet.Search &&
textEntryActive`, not on some coincidental path. Reopening search under the same restrictions was
allowed and opened in voice-prompt mode, which is what §4 of the spec requires.

## The cap proof

`SongRepository.searchSongs()` returned 41 rows for `worship`. While driving, tapping a result row
produced a media session with `queueTitle=null, size=21` — the platform's
`maxCumulativeContentItems`, not the repository's answer. `active item id=2` matched the row
tapped, third in the visible list.

This is the failure the plan named as A6's biggest correctness risk: the driver must play from the
list they can see. Queue size 21 with the correct index is that property observed end to end,
and `UxRestrictionState.cap()` is unit-tested and mutation-checked for the same rule.

## The album-search proof

Queried directly against Room on device (`run-as … sqlite3 databases/nyasa_player.db`):

| Predicate | Rows |
|---|---|
| `title LIKE %worship% OR artist_name LIKE %worship%` (pre-A6) | 27 |
| `… OR album_name LIKE %worship%` (A6) | 41 |

Fourteen songs are reachable only through the new album clause — `draw me close` and `more love`
by Michael W Smith on the album `worship`, `I Surrender All` on `iWorship Hymns`, and others. They
appeared in the shipped results list, so the DAO change is live rather than merely compiled.

## A defect found and fixed during this run

**The Search CTA rendered under `NO_KEYBOARD`.** A query typed while parked survives in
`AutomotiveSearchUiState.query`. When the vehicle then started moving, `CarSearchScreen` replaced
the field with the voice prompt but still rendered the gold **Search** button, because it was
gated on `query.isNotBlank()` alone.

Not a silent no-op — pressing it would have run a real search, and results while driving are
allowed — but the driver could no longer see the terms it would run, because the field they were
typed into was gone. The CTA is now gated on `canType` as well, for the same reason the `Songs`
shortcut is (D39). Re-verified on device: no CTA beside the prompt, including with `worship` still
held in state.

## Not verified

- **Search error state.** No way to force `searchSongs()` to fail on this image. A5 recorded the
  same limitation for retryable playback errors: `network speed gsm`, `network delay gprs` and
  `iptables -j REJECT` all failed to break the data path. The path is covered by unit tests
  (`a failed search reports an error and keeps the query available for retry`) and by the
  `errorMessage != null` branch of `CarSearchResultsScreen`.
- **Top-result card tap.** Verified by inspection only; a later row was tapped successfully and
  both call the same `onSongClick(results, song)`.
- **Recent-query de-dupe and the cap of five on device.** Unit-tested; only population was
  observed on device.
- **Auto-focus on open.** Deliberately not implemented — see D40.

## Observations

**Recent queries do not survive process death, as designed.** Every `force-stop` or reinstall
returned the sheet to its recent-empty state. That is D34 behaving, not a defect.

**Three ANRs occurred on a memory-starved emulator instance and none after a cold boot.** The
first instance was at 1878MB of 2012MB used, with `dumpsys car_service` itself hitting its 10s
timeout. Traces were pulled rather than guessed at:

- One ANR had the app's main thread `Native` / `state=S` parked in `nativePollOnce` — **idle**
  while input dispatch still timed out at 6.4s. That is the system failing to deliver the event.
- One was `Application does not have a focused window` during startup.
- One had the main thread `Runnable` inside a Compose draw pass, triggered by `input text
  "worship"` firing seven keystrokes with no gap. It did **not** reproduce at one keystroke per
  second.

After the cold boot, the full driving-state session ran with **zero** ANRs.

**Text entry is measurably heavier than the app's other interactions.** On the degraded instance,
typing seven characters at 1/second cost up to 240 dropped frames, against 33–47 for tab
switching on the same build and boot. The renderer is `ro.hardware.egl=emulation` and the build is
a debug build, so neither number transfers to a real head unit, and the gap was not reproduced on
the healthy instance. Recorded as something to measure on real hardware rather than as a defect.

**Taps are swallowed far more often than on A3–A5.** Several checklist steps needed two or three
attempts at the same coordinate, and one apparent "playback started from the wrong song" turned
out to be a swallowed tap, not a state bug. `mCurrentFocus` naming the activity remained
insufficient, exactly as the A4 record warns.
