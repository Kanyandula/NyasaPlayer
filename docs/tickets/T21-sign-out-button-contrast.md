# T21 - The sign-out confirm button fails WCAG AA

- **Slice:** A7 review finding — pre-existing, promoted rather than introduced
- **Depends on:** a palette decision (the design doc owns the contrast table)
- **Status:** Done — fixed in the A7 PR that filed it
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest detekt`
- **Design Reference:** `docs/aaos-DESIGN.md` "Contrast, measured"; D68
- **Risk Tags:** accessibility, in-vehicle legibility, design system
- **Affected Modules:** `:automotive`

## Problem

`CarSignOutConfirmation`'s confirm button is white text and a white icon on solid `CarSignOutRed`
(`#EF5350`). Measured, that is **3.49:1** — below WCAG AA's 4.5:1 for body text, and far below the
AAA bar `docs/aaos-DESIGN.md` claims for "every non-disabled pair on every surface it lands on".
The button's text is 20.sp Medium, which does not qualify for the large-text exemption (18pt
regular or 14pt bold).

The pairing is not new: it shipped inside `CarLibraryScreen` from A3 and A7 moved it verbatim when
sign-out was hoisted to the shell. What A7 changed is that `CarSignOutRed` is now a shared,
documented token, so the wrong pairing is easier to copy.

The other pairing is fine: `CarSignOutRow` puts the red on its own 15% wash, which is what the
contrast table actually measured.

## Scope

- Pick a fix and record it as a D-number:
  - a darker red for solid fills (`#C62828` measures 5.17:1 white-on-solid, `#B71C1C` 6.57:1), or
  - give the confirm button the wash treatment the row uses, so no solid pairing exists, or
  - keep the red and drop the doc's blanket AAA claim to a per-pair table.
- Add the chosen solid pairing to the contrast table with its real ratio. The table currently has
  no row for it.
- Correct the `CarSignOutRed` KDoc, which now names this ticket instead of claiming the pairing was
  measured.

## Out Of Scope

- Re-measuring the rest of the palette. Every other pair in the table was checked in A1.
- Changing `CarSignOutRow`. Its pairing passes.

## Acceptance Criteria

- Given the sign-out confirmation is open, then the confirm button's foreground/background pair
  measures at least 4.5:1, and the ratio is written in the contrast table.
- Given the design doc's contrast claim, then it is either true for every pair or scoped to the
  pairs it actually covers.

## Notes

Caught by the quality reviewer on the A7 pre-PR pass and confirmed by computing the ratio, not by
eye — the button is legible, which is why it survived a device pass and three slices.

## Outcome

Fixed in A7 rather than deferred, on request. `CarSignOutRedSolid` (`#C62828`) is a second token
for the fill role; `CarSignOutRed` (`#EF5350`) keeps the foreground role unchanged.

One token could not do both. Darkening the shared value to `#C62828` would have taken the confirm
button from 3.5:1 to 5.2:1 and, in the same move, dropped `CarSignOutRow`'s red-on-wash text from
4.6:1 to 3.3:1 — trading one AA failure for another. Measured both before choosing.

The contrast table now carries all three pairs, and the blanket "every non-disabled pair clears
AAA" claim is scoped to exclude the two destructive ones, which clear AA.
