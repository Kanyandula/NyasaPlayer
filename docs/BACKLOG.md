# Backlog — after the AAOS PRD

`docs/AAOS_PRD.md` is finished as written before anything here is picked up (owner, 2026-09-15).
One line per item. An item becomes a `docs/tickets/` entry only when it is picked up, or if it turns
out to be a bug a driver or user would hit, or a safety gap. Ticket numbers below are the ones the
items were filed under; their files are readable in git with `git show 48e1442:docs/tickets/<file>`.

## Filed, parked

- **T20** — an audio-quality preference. Nothing in `PlaybackService` reads one, so a row would
  persist a value no one observes (D70). First check whether the catalogue has more than one
  rendition per song; if not, it is a backend ticket.
- **T19** — car phone and email sign-in; deferred past ship, the PRD's §12 exception (D70).
  `AuthRepository` already has both methods. Typing is refused under `NO_KEYBOARD`, and a head unit
  with no SIM should not be offered phone sign-in at all.
- **T18** — car PIN opt-in; deferred past ship, the PRD's §12 exception (D67). Spec first what the
  PIN protects, where its salted hash lives and what happens after N failures — or close it by
  dropping US-3. What must not ship is a PIN that gates nothing.
- **T8** — hoisting the search draft query out of the app-root state. The mechanism is real (a
  keystroke invalidates `AuthenticatedApp`), the cost is not: all 109 composables are skippable and
  seven keystrokes dropped 0 frames on a healthy emulator (2026-08-21). Reopen if typing lag is
  reported on a real head unit.
- **T33** — a like made while Firestore was unreachable may not survive the reconnect; seen once in
  T13's offline pass, not reproduced. Reproduce by going offline at the Firestore level, not just
  airplane mode, and record what the Snackbar host does — a failed write with no Snackbar is the
  defect either way.
- **T34** — split `AutomotiveContentViewModel`, past detekt's 16 in-class threshold and held open by
  the file-level suppression D23 recorded as debt. Split along downloads; done when the
  `TooManyFunctions` suppression can be deleted.
- **T35** — four owed device checks, none a suspected bug: on mobile, T3's D55 index fix and T7's
  skeletons; on the car, T13's `skipNext` repeat-all wrap and a driving-state pass after T13/T14.
  The phone sitting of 2026-09-19 cleared five others (T10, T13, T14, T25, T29) and not these.
- **T36** — 32 of `:core:playback`'s tests were never challenged by the 2026-09-19 mutation sweep:
  17 in `MediaBrowseTreeTest` (`getItem` and `search` mutations did not compile), 5 in
  `PlayableItemsTest` (covered by T31/T32's own mutation checks), 4 in `ConnectedTransportTest`, 3 in
  `ControllerConnectionTest`, 2 in `PlaybackStatePersistenceTest`, 1 in `SharedControllerFutureTest`.
  No weakness shown, none ruled out. Method: `docs/CODING_GUIDELINES.md` → Testing.
- **T37** — PRD §12 criterion 1 says the screens "match `docs/aaos-DESIGN.md`"; only three floors
  were ever measured, so there is **no evidence either way**. Each finding says whether it was
  checked against the source; nothing in it is a priority anyone set
  (`docs/tickets/T37-design-conformance-never-measured.md`).

## Known, not fixed

- `songs/0CvN4z9xMVoRSi6iTjgp` (`mediaId` 190, "mighty") is 705 bytes of zeros in Firebase Storage
  with `durationMs` 0 — a broken upload in the catalogue, not an app bug (A9 device pass,
  2026-09-17).
- A 404 is called "No Connection" on the car: `onPlaybackError` treats `cause is IOException` as a
  network failure, and a bad HTTP response or an unparseable file is an `IOException` too
  (`AutomotivePlayerViewModel`).
- Offline, a seek backwards fails even inside the current track: ExoPlayer keeps no back-buffer, so
  audio already played has to come from the network again, and the error overlay shows (A8).
- After a track change while paused, the car's scrubber keeps the previous track's position until
  playback starts (A8).
- `CarRestrictionDialog`'s card has no touch guard, so tapping the body dismisses it while the driver
  may still be reading the reason. Changing a refusal dialog's dismissal is a product call.
- `CarAlbumScreen`, `CarPlaylistScreen` and `CarArtistScreen` are identical forwarders to
  `CarDetailBody`, differing in one empty-state string; selecting it at the one call site removes
  ~110 lines. The exact/prefix/substring match ladder exists four times (two DAO `CASE` blocks,
  `matchQuality()`, `FirebasePlaylistRepository.nameMatchTier`) — a drift risk, not a task.
- `core/data`'s test `FakeSongRepository.getSongsByIds` does not preserve request order, unlike every
  real implementation.
- `PlayerViewModel` has no unit tests. `:app` has no Robolectric, but `:core:playback` drives a real
  `MediaController` on the JVM (`aaos-DESIGN.md` D64), so "the ViewModel is not JVM-testable" is an
  assumption to check.
- `CarAuthScreen` shows the wordmark "NyasaPlayer", where the system bar and the brand rule say
  "Nyasa Music" (T37 finding 1).
- 11 of the 12 gradient colours at the bottom of `AutomotiveColors.kt` have no reference; only
  `CarGradientOrange` is used.
- `SongDownloadManager.cancelDownload` has no caller on either surface.
- `CarSystemBar`'s disabled-control branch is unreachable: every caller passes a click handler.
- Mobile Home's notifications button does nothing (`HomeScreen.kt`, `onClick = { /* notifications */ }`).
- `:app` may not need `media3-exoplayer` or `media3-ui` — it imports neither. Unverified without a
  build.
- The outlined `CarPillButton` border is 1.4:1 against WCAG 1.4.11's 3:1 for component boundaries (non-text, outside NFR-2).
- The queue's `RemoveConfirmDialog` renders inline inside its lazy item (`CarQueueScreen.kt:308`), not as a modal over the queue.
- Six resolution sites still read the download index without awaiting it — the `resolveLocalUri`
  calls in `playSong` (two) and `shufflePlay` (one) of both `PlayerViewModel` and
  `AutomotivePlayerViewModel`. T32 gave the two suspend paths an await and left these: they are
  non-suspend, and a tap is far slower than the load. If one ever lost the race the user would see
  "no connection" on a downloaded song.
- `onControllerLost`'s single-attempt guard (`compareAndSet`) is untested on both sides, and
  untestable in the current harness: `ControllerConnection.reconnect()` resolves inside the first
  command, so no second command can arrive mid-rebuild. Two tests claimed to cover it and did not
  (T14's, renamed 2026-09-19; T27 hit the same wall). Testing it needs a seam that can hold a
  rebuild open.
- `CarNavRail` maps `CarScreen` twice — `iconFor()` and `labelFor()` are back-to-back `when`
  blocks over the same enum. `:app` already solved this shape with a
  data-driven `NavItem(route, labelResId, icon)` list (`NyasaBottomNavBar.kt:42`). Low risk today:
  both `when`s are exhaustive, so a new `CarScreen` fails to compile in both places. But
  `labelFor` returns hardcoded strings where `:app` uses `@StringRes`, so **the rail is not
  localisable** — that is the part worth fixing if either is. Raised by a review of PR #92.
- No third-party attribution exists for the ~35 Material-derived icon paths in
  `core/common/.../ui/icons/NyasaIcons.kt` — `SearchIcon`, `HeartIcon` and the rest are Material
  glyphs (Apache-2.0) copied verbatim, and the repo has no NOTICE file, no OSS-licenses screen,
  and nothing under Settings' About. Pre-existing and repo-wide, not introduced by any one icon.
  A NOTICE file is the cheap fix if this ever matters for distribution.
- The measurement harness models ~133dp more vertical room than a real head unit.
  `InContentSlot` (`CarUiCases.kt`) subtracts the app's own chrome — nav rail, system bar,
  mini-player, margins — but not the OS status and climate bars a device also loses: at 1024x768
  that left 480dp in the harness against ~347dp measured on the device. Two card-clipping defects
  reached a device through a green suite because of it, and `the first content card ... drawn
  whole` still passes on the broken build (mutation-checked). Teaching `ContentSlot` the OS chrome would re-baseline all 120
  cases, so it is its own piece of work. The full player has the same gap outside the slot: at the
  800dp canvas the Up Next row passed while a 628dp AVD window clipped it off. #103 pins the
  player cases to 628dp; setting the whole canvas to h628dp also squeezes the loading skeletons on
  Home, Favourites and the three detail screens, and one Downloads row, which is the rest of this.
- Detekt never scans test sources. `build.gradle.kts` scopes `source.setFrom(...)` to
  `*/src/main/java` only, so the 90-odd files under `src/test` are unlinted — an unused import in
  `CarTouchTargetMeasurementTest` passed a green `detekt` run and was found by review instead.
  Pre-existing, and adding the test roots will surface a backlog of its own.
- The full player's transport row does not fill the column it sits in. Five controls at 76/76/96/76/76
  with 23dp gaps come to 492dp; the column is capped at 760dp on a 1440dp window, so ~268dp sits
  empty after repeat. This is the same slack #108 removed by narrowing the column to the design's
  536dp — sizing art and column from the window (2026-09-28) bought back the screen's side margins
  and spent some of it here. It reads as left-alignment rather than a hole, because the scrubber and
  the Up Next card span the full width and hold the right edge, so it is recorded rather than
  ticketed. If it ever stops reading that way the knobs are a narrower cap or a respaced transport,
  and the design's 23dp rhythm is what a respace would cost.
- The car's queue sheet opens on tracks already played, under a heading about what is next. Parked,
  `queueDisplayItems` (`QueueDisplayItem.kt:22`) lists the whole queue from index 0 and
  `CarQueueScreen` never scrolls to `currentIndex`, so the sheet headed "Up Next · 4 songs" showed
  five earlier Gospel tracks while the current one and the Up Next card's "Jesu, Joy of Man's
  Desiring" sat below the fold (AVD, 2026-10-10, a restored queue). The card and the sheet read the
  same snapshot and agree; only the starting scroll misleads. Driving pages differently — the
  capped list starts at the current item, but only once it falls past the first page — and was not
  checked. The likely fix is `rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)`
  for the parked list.
- The three Robolectric measurement classes can fail as a group under parallel Gradle workers,
  with `NoSuchMethodError` and "Could not write XML test results"; `--max-workers=1` passes
  cleanly. Reported by a review of PR #95 and reproduced there on a fresh daemon. Not reproduced
  across roughly a dozen later runs (2026-09-20), so it is recorded rather than diagnosed — but a
  gate that fails on worker count and not on code is one nobody will trust for long.
