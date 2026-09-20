# Backlog — after the AAOS PRD

`docs/AAOS_PRD.md` is finished as written before anything here is picked up (owner, 2026-09-15).
One line per item. An item becomes a `docs/tickets/` entry only when it is picked up, or if it turns
out to be a bug a driver or user would hit, or a safety gap.

## Specced or filed tickets, parked

- **T20** — audio quality preference (`docs/tickets/T20-audio-quality-preference.md`).
- **T19** — car phone and email sign-in; deferred past ship, the PRD's §12 exception (`docs/tickets/T19-car-auth-phone-and-email.md`).
- **T18** — car PIN opt-in; deferred past ship, the PRD's §12 exception (`docs/tickets/T18-car-pin-opt-in.md`).
- **T8** — search draft-query hoist, deferred on measurement (`docs/tickets/T8-automotive-search-draft-query-hoist.md`).
- **T33** — a like made while Firestore was unreachable may not survive the reconnect; seen once,
  not reproduced (`docs/tickets/T33-like-across-reconnect.md`).
- **T34** — split `AutomotiveContentViewModel`; 24 functions against detekt's 16, held open by the
  file-level suppression D23 says this project does not use
  (`docs/tickets/T34-split-automotive-content-viewmodel.md`).
- **T35** — the four owed device checks, collected into one sitting
  (`docs/tickets/T35-owed-device-checks.md`).
- **T36** — `MediaBrowseTree`'s 17 tests, never challenged by a mutation that compiled
  (`docs/tickets/T36-sweep-mediabrowsetree-tests.md`).
- **T37** — PRD §12 criterion 1 says the screens "match `docs/aaos-DESIGN.md`"; only three floors
  were ever measured, so there is **no evidence either way**. 10 live findings, 8 checked against
  the source and 3 not; nothing in it is a priority anyone set
  (`docs/tickets/T37-design-conformance-never-measured.md`).

## Owed phone checks — cleared 2026-09-19

All five ran on `Pixel_9_Pro_Fold_API_35` in one sitting: T25 (release crash sent, debug crash not,
one event on the dashboard), T29 (offline banner, refusal wording, recovery), T13 (transport set,
`dismiss()`, the offline-buffering pause), T14 (both ways of finishing the Activity), T10 (restore
after `force-stop`, with the service watched from the first launch).

Still owed on mobile, and not covered by that sitting: T3's D55 index fix and T7's skeletons, both
named in T10's and T14's own "still owed" notes. Still owed on the car: T13's `skipNext` repeat-all
wrap and a driving-state pass (T13, T14). All four are now collected in
`docs/tickets/T35-owed-device-checks.md`.

## Known, not fixed

- `songs/0CvN4z9xMVoRSi6iTjgp` (`mediaId` 190, "mighty") is 705 bytes of zeros in Firebase Storage
  with `durationMs` 0 — a broken upload in the catalogue, not an app bug
  (`docs/AAOS_A9_VERIFICATION.md`, 2026-09-17).

- A 404 is called "No Connection" on the car (`docs/AAOS_A8_VERIFICATION.md`, "Findings recorded, not fixed here").
- The outlined `CarPillButton` border is 1.4:1 against WCAG 1.4.11's 3:1 for component boundaries (non-text, outside NFR-2).
- The queue's `RemoveConfirmDialog` renders inline inside its lazy item (`CarQueueScreen.kt:308`), not as a modal over the queue.
- Six resolution sites still read the download index without awaiting it —
  `PlayerViewModel.kt:190,195,213` and `AutomotivePlayerViewModel.kt:273,280,297`. T32 gave the two
  suspend paths an await and left these: they are non-suspend, and a tap is far slower than the
  load. If one ever lost the race the user would see "no connection" on a downloaded song
  (`docs/tickets/T32-download-path-cache-race.md`, 2026-09-19).
- 32 of `:core:playback`'s 106 tests were never challenged by the 2026-09-19 mutation sweep —
  mostly `MediaBrowseTree`'s `getItem` and `search`, whose mutations would not compile
  (`docs/TEST_MUTATION_SWEEP.md` lists them). No weakness shown, none ruled out either. The
  17-test `MediaBrowseTree` block is now `docs/tickets/T36-sweep-mediabrowsetree-tests.md`.
- `onControllerLost`'s single-attempt guard (`compareAndSet`) is untested on both sides, and
  untestable in the current harness: `ControllerConnection.reconnect()` resolves inside the first
  command, so no second command can arrive mid-rebuild. Two tests claimed to cover it and did not
  (T14's, renamed 2026-09-19; T27 hit the same wall). Testing it needs a seam that can hold a
  rebuild open.
- A like may not survive a reconnect after Firestore was unreachable — seen once, not reproduced
  (`docs/T13_VERIFICATION.md`, 2026-09-19). Ticketed as T33.
- `AutomotiveContentViewModel` is past detekt's function threshold and now owns downloads too; its file-level `TooManyFunctions` note says the next slice to touch it should split it, and A9 added to it instead (PRD §6.3 names the content VM as screen 15's data source). Ticketed as T34.
- `CarNavRail` maps `CarScreen` twice — `iconFor()` and `labelFor()` are back-to-back `when`
  blocks over the same enum (`CarNavRail.kt:150,157`). `:app` already solved this shape with a
  data-driven `NavItem(route, labelResId, icon)` list (`NyasaBottomNavBar.kt:42`). Low risk today:
  both `when`s are exhaustive, so a new `CarScreen` fails to compile in both places. But
  `labelFor` returns hardcoded strings where `:app` uses `@StringRes`, so **the rail is not
  localisable** — that is the part worth fixing if either is. Raised by a review of PR #92.
- No third-party attribution exists for the ~35 Material-derived icon paths in
  `core/common/.../ui/icons/NyasaIcons.kt` — `SearchIcon`, `HeartIcon` and the rest are Material
  glyphs (Apache-2.0) copied verbatim, and the repo has no NOTICE file, no OSS-licenses screen,
  and nothing under Settings' About. Pre-existing and repo-wide, not introduced by any one icon.
  A NOTICE file is the cheap fix if this ever matters for distribution.
