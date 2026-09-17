# T30 - The Remove all downloads confirmation is clipped on a 768dp-tall head unit

- **Slice:** AAOS downloads — A9 follow-up
- **Depends on:** A9 (merged, PR #67)
- **Status:** Filed, not specced
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

## Notes

- **Why it is not "just cosmetic".** The dialog is destructive and the labels are the only thing
  distinguishing Cancel from Remove. A driver who cannot read them can still tap them.
- The device pass found this by hand. It is the second finding the 800dp canvas hid — the first was
  a text node measured only partly in view, fixed in `c3a0b9c`.
