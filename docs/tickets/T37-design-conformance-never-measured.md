# T37 - "Matches the design" was never measured beyond three floors

- **Slice:** AAOS UI/UX conformance
- **Depends on:** nothing. Wants a head unit for the part an emulator cannot close (open question Q3).
- **Status:** Filed 2026-09-19 after an audit of the shipped car UI against the live design
  documents. Findings below were spot-checked against the source, not taken on trust.
- **Verification Command:** `./gradlew :automotive:testDebugUnitTest` (the three measurement tests),
  plus whatever this ticket adds
- **Design Reference:** `docs/aaos-DESIGN.md` (frontmatter type and spacing scales, §Components,
  §Colors), `docs/AAOS_SCREEN_CONTRACT.md`, `docs/AAOS_PRD.md` §7 and §12, `docs/AAOS_SHIP_RECORD.md`
- **Risk Tags:** unmeasured claim, visible drift, docs that disagree with code
- **Affected Modules:** `:automotive`, and the design documents themselves

## Problem

PRD §12 criterion 1 is *"All 20 screens are implemented and **match** `docs/aaos-DESIGN.md`"*. The
screen count was checked. "Match" never was, beyond three floors that `CarUiCases.kt`'s 84 cases
feed:

| Test | Asserts | Result at ship |
|---|---|---|
| `CarTouchTargetMeasurementTest` | ≥76dp | 695 nodes, 0 below |
| `CarTextContrastMeasurementTest` | ≥7:1 | 1103 text nodes, 0 below, 55 exempt |
| `CarTextSizeMeasurementTest` | ≥14sp, ≥18sp for a control's own label | added late by T30, which **found two real violations** |

Those are floors. They cannot see a wrong type size, a wrong corner radius, a wrong spacing value,
or a component built to the wrong shape — only one that is illegibly small or invisible.
`docs/AAOS_SHIP_RECORD.md:18` already says as much about criterion 1, and T30 is the proof the
concern is not theoretical: the type floors were "an unmeasured prose claim" until something
measured them, and two screens were wrong.

**First, what is *not* the problem.** `docs/AAOS_UI_REDESIGN_PLAN.md`'s 2026-04-23 "template only,
no custom screens" decision is contradicted by every custom screen that ships — and that is
sanctioned, not drift. That document carries a `⛔ SUPERSEDED — 2026-08-02` banner, and PRD §3.3
records the reversal: the custom launcher is the product. The governing documents are the PRD, the
screen contract and `aaos-DESIGN.md`. Against those, 18 of 21 contracted screens ship, 2 are owner
deferrals (T18's PIN opt-in, T20's audio quality) and 2 are deliberate non-screens (D71).

### Findings, verified against the source

| # | Finding | Evidence |
|---|---|---|
| 1 | **The sign-in screen shows the wrong brand.** `aaos-DESIGN.md:152` — *"The product name is **Nyasa Music**. It is the only brand name that may appear in any screen."* | `CarAuthScreen.kt:90` renders `"NyasaPlayer"`. `CarSystemBar.kt:110` and `CarSettingsScreen.kt:66` both get it right, so it is one stale string on the first screen a driver sees |
| 2 | **`CarChip` is built, unused, and two documents say it does not exist.** | Defined at `CarControls.kt:41`; the only call sites are `CarUiCases.kt:489-490`, a test. `AAOS_SCREEN_CONTRACT.md:82` says *"not built"*; `CarSearchScreen.kt:349` explains why the ghost `CarPillButton` is used instead (D39) |
| 3 | **Three contract components were never built**, and transport is duplicated because of one of them. | No `CarIconButton`, `CarPlaybackControls` or `CarParkedBadge` anywhere in `automotive/src`. `CircleIconButton` (`CarFullPlayerScreen.kt:417`) is a private local copy; `MainControls`/`PlayPauseButton` and `MiniPlayerControls` (`CarMiniPlayer.kt:155`) are two copies of the same transport — against the contract's *"Prefer one shared component with variants over local copies"* |
| 4 | **The type scale is not tokenised.** `aaos-DESIGN.md` frontmatter names 7 styles; `AutomotiveDimens.kt` holds only dp. Sizes are private per-file vals: 14/16/18/20/22/24/26/30/34/36/40 sp. | Screen titles are 30sp, 34sp (`CarDetailScreen.kt:41` and 3 others) and 36sp. 40sp exists, but only on `CarEmptyState.kt:21` and `CarRestrictionDialog.kt:32` — not on any screen title, which is what the scale's `screen-title` names |
| 5 | **Corner radii disagree with the spec and with each other.** | Primary CTA is fully rounded (`CarControls.kt:29`, pill), design says 14px. Text input is 20dp (`CarSearchScreen.kt:68`), design says 16px. Three independent private 16dp list-row constants (`CarQueueScreen.kt:64`, `CarDownloadRow.kt:49`, `CarSheetChrome.kt:32`). `CarRestrictionDialog.kt:52` bypasses `CarModalCard`, so modals have two different corners |
| 6 | **The system bar adds an unspecified gold badge.** `aaos-DESIGN.md:276` specs the wordmark 24px from the left edge. | `CarSystemBar.kt:90-116` puts a gold circular music-note badge before it — and §Colors restricts gold to the active nav item, focused border, primary CTAs, the play button and the progress fill |
| 7 | **Two PRD/contract claims describe behaviour that does not ship.** | PRD §7.3 says ambient gradients follow the album artwork while parked; `CarAmbientBackground.kt:39` says the hue is fixed and artwork-following was deferred in A2 (D4). The contract lists "screen cross-fade" as parked motion; no `Crossfade`/`AnimatedContent` exists under `automotive/src/main` |
| 8 | **Home's sections are not the specced ones.** PRD §6.3 and contract screen 3 say Continue Listening cards, Your Mixes, Recommended. | `CarHomeScreen.kt:145` is "Continue Listening" as **track rows**, `:156` is "Popular Now". "Your Mixes" and "Recommended" do not exist; an unspecified `ResumeHero` (`:210`) was added |
| 9 | **D12 was superseded and never annotated.** `aaos-DESIGN.md:331` still forbids a Download button on the album screen and download-progress state. | A9 shipped both (`CarDownloadItem.kt:93`, `CarDetailScreen`). D14 got a `**Closed by D68:**` line at `:344`; D12 got nothing |
| 10 | **`getMaxRestrictedStringLength()` (120 chars) is still unenforced.** `aaos-DESIGN.md` §Driving restrictions says it *"would need real enforcement before shipping"*. | `UxFlags.kt` carries only `maxContentDepth` and `maxCumulativeContentItems`. `AAOS_SHIP_RECORD.md` does not mention it |
| 11 | **Three phases have no verification document.** | There is no `AAOS_A1_`, `AAOS_A2_` or `AAOS_A7_VERIFICATION.md`. A2 delivered the **entire chrome contract** — the thing PRD §7.2 exists for *"because the original generated designs drifted"* — and has no standing record; A7's is *"parked and driving pass in the PR"* |

## Scope

Two halves, and the second is cheap only after the first.

- **Decide, per finding, which side is wrong.** Several of these are the document being stale, not
  the code (D12, `CarChip`'s contract row, arguably the ambient gradient). A document amended with
  a dated reason is a fix; a document quietly left wrong is the thing that made this audit
  necessary.
- **For the ones where the code is wrong**, fix them. Finding 1 is a one-line change on the highest
  visibility screen and should not wait for the rest.
- **Leave a measurement behind where one can exist.** The type and spacing scales are the natural
  candidates: `CarUiCases.kt` already renders every screen, and `CarTextSizeMeasurementTest` already
  walks every text node — asserting *membership in the scale* is a smaller change than it looks, and
  it is exactly the class of check T30 proved worth having.

## Out Of Scope

- Reopening the 2026-04-23 template-only decision. It was reversed on 2026-08-02 and PRD §3.3 is
  the record; this ticket is not a second look at that.
- T18 and T20's deferred screens, which are the PRD's own §12 exceptions.
- Any new screen, section or feature. "Your Mixes" and "Recommended" (finding 8) are a **decision**
  to record either way — build them or amend §6.3 — not an invitation to design them here.
- Open question Q3 (15px secondary text at arm's length). That needs real head-unit hardware and
  cannot be closed by anything in this ticket.

## Acceptance Criteria

- Every finding above has a resolution recorded with a date: code changed, document amended, or
  accepted with a reason.
- Given the type scale has been reconciled, when the measurement suite runs, then a size outside the
  agreed scale fails the build — or the ticket says explicitly why that check was not added.
- `CarAuthScreen` renders "Nyasa Music".
- No component is defined in `automotive/src/main` with zero production call sites.

## Notes

The audit that produced this list is not itself evidence — it is a list of places to look, and every
row above was spot-checked against the source before being written down. One claim in the draft was
wrong on that check (*"nothing is 40sp"*) and is corrected in finding 4. Anything acted on here
should be re-confirmed the same way.
