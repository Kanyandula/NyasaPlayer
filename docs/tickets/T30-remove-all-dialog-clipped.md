# T30 - The Remove all downloads confirmation is clipped on a 768dp-tall head unit

- **Slice:** AAOS downloads — A9 follow-up
- **Depends on:** A9 (merged, PR #67)
- **Status:** Fixed — see Outcome; device re-check owed
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest --tests '*CarDownloadsScreenTest*'`
- **Design Reference:** `docs/aaos-DESIGN.md` → modals; A9's `CarDownloadsScreen`
- **Risk Tags:** driver-facing, layout, screen height
- **Affected Modules:** `:automotive`

## Problem

On `AAOS_AOSP_33_userdebug` (1024×768 px, so 768dp tall), the "Remove all downloads?" confirmation
draws its Cancel and Remove buttons across the dialog's bottom edge: both are cut in half
horizontally. They still take a tap — the pass on 2026-09-17 removed all downloads through the
clipped button — but a driver sees two half-drawn controls and cannot read either label in full.

Screenshot: `dl-remove-all-confirm.png` in the 2026-09-17 device pass
(`docs/AAOS_A9_VERIFICATION.md` → "Found on this pass").

Nothing caught it because the measurement suite renders every case at `w1280dp-h800dp`
(`CarUiCases.MeasurementQualifiers`), where the dialog fits. The emulator the car work is verified on
is 32dp shorter.

## Scope

- Make the dialog fit at 768dp: the card sizes to its content, or its content scrolls, rather than
  the buttons being clipped.
- Add the shorter canvas to the measurement suite so a clipped control fails a run. `w1280dp-h768dp`
  matches the emulator; the head units the PRD names are 1080p, so the suite may want both.

## Out Of Scope

- Any other modal. The sign-out confirmation was measured at 344dp tall against 800dp and fits; it
  should be re-checked at 768dp with this change, not redesigned.

## Acceptance Criteria

- Given a 768dp-tall canvas, when the Remove-all confirmation is shown, then both buttons render
  whole, inside the card.
- Given the measurement suite, then a control clipped by its own container fails the run.

## Outcome

The cause was not clipping by the card but **squashing inside it**: a `Column` hands each child
whatever height is left, so `ConfirmButton`'s `Modifier.height(CarTouchTargetSize)` was measured at
28dp when the slot was short. The buttons were their full width, a third of their height, and still
took a tap — which is why every gate passed and only a screenshot caught it.

`CarModalCard` now scrolls when the content does not fit, which makes the Column measure its
children at their own heights and move the overflow instead of crushing it. Every modal that shares
the card gets the same guarantee.

Measured before and after in `CarUiCases`' "cramped slot, remove-all confirmation open" case:
Cancel and Remove went from 200 x 28 dp to 200 x 76 dp.

`CarTextSizeMeasurementTest` is the type measurement NFR-3 never had: no text under 14sp, no
control's own label under 18sp, read from `GetTextLayoutResult` rather than the source. It found two
live violations — the offline banner at 12sp (`:core:common`, so the phone had it too) and the
Downloads screen's Remove All at 16sp. Rail tabs are the design's recorded exception and now carry
`Role.Tab`, which is also what TalkBack should announce.

**Owed:** the on-device re-check. Reproducing the dialog needs downloads, which needs an album in
the catalogue; the test album added for A9 was deleted at the owner's request
(`docs/AAOS_A9_VERIFICATION.md`).

## Notes

- **Why it is not "just cosmetic".** The dialog is destructive and the labels are the only thing
  distinguishing Cancel from Remove. A driver who cannot read them can still tap them.
- The device pass found this by hand. It is the second finding the 800dp canvas hid — the first was
  a text node measured only partly in view, fixed in `c3a0b9c`.
