# AAOS ship record

What `docs/AAOS_PRD.md` §12 asks for, and what is true of `main` on the date below. One line per
criterion, each pointing at the evidence rather than restating it.

- **Date:** 2026-09-16
- **Commit:** `4b2b882` — A9 (#67) on top of MG-2 (#66) and Exit (#63)
- **Gate run today:** `./gradlew :automotive:assembleOemDebug :automotive:assembleOemRelease
  :automotive:assemblePlaystoreDebug :automotive:assemblePlaystoreRelease :app:assembleDebug
  :app:assembleRelease test detekt :automotive:lintOemDebug :automotive:lintPlaystoreDebug
  :app:lintDebug :core:data:lintDebug` — BUILD SUCCESSFUL, **818 tests, 0 failures**, detekt 0 issues,
  lint clean

## §12, line by line

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | All 20 screens implemented, matching the design | **Met, with the recorded exception** | 18 screens ship. Screen 2 (PIN opt-in, T18) and screen 1's phone and email sign-in (T19) are deferred past ship by the owner's exception in §12 itself; screen 1 ships with Google sign-in. The type floors were an unmeasured prose claim when this was written; T30 measured them on 2026-09-18, found two violations (the offline banner at 12sp, Downloads' Remove All at 16sp), fixed both, and left `CarTextSizeMeasurementTest` behind as the evidence |
| 2 | Zero interactive controls below 76dp | **Met** | `CarTouchTargetMeasurementTest`: 119 cases in 161 frames, **695 interactive nodes, 0 below 76dp** |
| 3 | Every non-disabled text/surface pair ≥ 7:1 | **Met, with the recorded exception** | `CarTextContrastMeasurementTest`: **1103 text nodes, 0 below 7:1**, 55 disabled and exempt, 0 seen only partly in view. The two destructive pairs clear AA and are recorded in `docs/aaos-DESIGN.md` → "Contrast, measured" |
| 4 | Every §6.2 restriction enforced, eviction included, verified against a real driving-state transition | **Met** | Q1 was answered yes: `docs/AAOS_DRIVING_STATE_TESTING.md` has the recipe, and the passes are in `docs/AAOS_A5_VERIFICATION.md`, `AAOS_A6_VERIFICATION.md`, `AAOS_T5_T6_VERIFICATION.md`, `AAOS_A8_VERIFICATION.md` and `AAOS_A9_VERIFICATION.md`. A9's download action was closed on 2026-09-17 once a test album existed: download, offline playback from the file, remove one and Remove All all ran on the car. The one piece still resting on unit tests is the Download control's own "Parked only" state while driving, which the outer drill-down refusal keeps unreachable |
| 5 | Both variants build, test and lint green; `oem` passes §8.3, `playstore` passes §8.2 | **Met** | The gate above covers both flavors. Manifest gates re-checked today against the release APKs — see the table below |
| 6 | Detekt reports zero issues | **Met** | 0 issues in today's run |
| 7 | `AAOS_UI_REDESIGN_PLAN.md` no longer contradicts the shipped architecture | **Met** | Superseded banner added 2026-08-02 |

## Manifest gates, measured today

Against `automotive-oem-release-unsigned.apk` and `automotive-playstore-release-unsigned.apk`,
built from `4b2b882`:

| Flavor | Launcher activities | Exported activities | `distractionOptimized` |
|---|---|---|---|
| `oem` | `AutomotiveActivity` (OG-1) | `AutomotiveActivity`, `GenericIdpActivity`, `RecaptchaActivity` | `AutomotiveActivity` = true (OG-2) |
| `playstore` | none (MG-1) | `GenericIdpActivity`, `RecaptchaActivity` | none |

The two Firebase Auth activities are MG-2's allow-list, recorded with their reasons in
`docs/AAOS_COMPLIANCE.md` → "MG-2's allow-list". Neither declares `distractionOptimized`, so the
platform blocks both in motion, which is what OG-4 asserts of a sign-in flow. MG-3, MG-5, MG-6 and
MG-7 hold: the media service is present and exported, the automotive feature is required, the
descriptor declares `<uses name="media" />`, and `playstore` ships no app activity at all.

## What is owed

- **T30** — the Remove-all confirmation's buttons are clipped on a 768dp-tall head unit, found by
  hand on 2026-09-17 because the measurement suite renders at 800dp
  (`docs/tickets/T30-remove-all-dialog-clipped.md`). Driver-facing, so it is a ticket rather than a
  backlog line. Fixed and device-verified 2026-09-18; merged as #71.
- **The owed phone checks are cleared** (2026-09-19): T25, T29, T13, T14 and T10 all ran on
  `Pixel_9_Pro_Fold_API_35`. They were mobile-side debts, not release gates for the car, but they
  were the last unverified claims either surface carried. Two new mobile findings went to
  `docs/BACKLOG.md`, neither driver-facing.
- **T18 and T19**, deferred past ship by the §12 exception, and parked in `docs/BACKLOG.md`.
- **The `HR-*` host-render smoke tests** (§8.4) are not part of this release: they run before a Play
  submission decision, which §3.2 makes a later business call.
- Everything else outstanding is one line each in `docs/BACKLOG.md`.

## Q3, still open

Is 15px acceptable for secondary text at arm's length? The rail label is now 14px and track-row
artist names 15px, both above the design's floor, but the floor itself has never been judged on real
head-unit hardware. `docs/AAOS_PRD.md` §11 keeps the question open; the conversion risk it names
(design px against hardware dp at unknown density) cannot close on an emulator either.
