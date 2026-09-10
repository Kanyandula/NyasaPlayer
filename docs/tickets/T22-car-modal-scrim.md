# T22 - Three copies of the same modal scrim

- **Slice:** A7 review finding — pre-existing duplication
- **Depends on:** nothing
- **Status:** Done
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest detekt`
- **Design Reference:** `docs/aaos-DESIGN.md` D68
- **Risk Tags:** drift, design-system consistency
- **Affected Modules:** `:automotive`

## Problem

`CarSignOutConfirmation`, `CarErrorOverlay` and `CarRestrictionDialog` each carry their own copy of
the same modal shell: a full-screen `Box` with `CarScrim`, dismiss-on-tap, centred content, a card
at `fillMaxWidth(0.5f)`, `RoundedCornerShape(24.dp)`, `CarGlass` and `padding(48.dp)`.
`ModalWidthFraction = 0.5f` is declared privately in two of them.

A7 did not create this — it inherited it when the sign-out modal moved out of `CarLibraryScreen` —
but it did turn the third copy into a public component, which is how a shape like this becomes a
fourth copy.

## Scope

- One `CarModalScrim(onDismiss, content)` in `auto/ui/components/`, owning the scrim, the width
  fraction, the corner radius, `CarGlass`, the padding and `carConsumeTouches()`.
- Move all three callers onto it. Delete the two private `ModalWidthFraction` constants.

## Out Of Scope

- Changing any modal's visual result. This is a refactor; the pixels should not move.
- `CarQueueScreen`'s sheet, which is a full-screen surface, not a scrimmed modal.

## Acceptance Criteria

- Given the three modals, then each renders from the shared scrim and no file declares its own
  scrim geometry.
- Given the existing Robolectric tests for the restriction dialog and the sign-out confirmation,
  then they pass unchanged.

## Notes

Deliberately not folded into A7: it touches two files the A7 diff never opened, and the slice had
already had its device pass.

## Outcome

Two components, not one — the ticket's plan would have moved pixels it promised not to.

`CarModalScrim` owns the backdrop, the dismiss tap and the centring. All three modals share it
verbatim, so that part of the ticket held.

The **card** did not. `CarRestrictionDialog` is a fixed `width(780.dp)` with `CarCardCornerRadius`
(20dp, not the modals' 24dp) and `padding(44.dp)`, so folding the card geometry into the scrim as
the ticket described would have resized and re-rounded it. The half-width glass card is therefore
`CarModalCard`, used by the sign-out and error modals only, and the restriction dialog keeps its
own Column inside the shared scrim.

Both private `ModalWidthFraction` constants are gone. Net -56 lines.

### A claim this ticket nearly shipped

While writing it up I asserted that `CarErrorOverlay`'s `clickable(enabled = false, onClick = {})`
did not consume the tap, and that tapping the error card therefore dismissed it. **That is false.**
Measured with a throwaway Robolectric probe:

| Card guard | Taps reaching the scrim |
|---|---|
| `carConsumeTouches()` | 0 |
| `clickable(enabled = false)` | 0 |
| no guard | 1 |

A disabled clickable blocks the tap just as well. The reason to prefer `carConsumeTouches` is
semantics — a disabled clickable publishes an interactive-but-disabled node to accessibility
services, announcing a control that never existed (FR-2.6) — not dismissal. Both KDocs were
corrected before commit.

`CarModalTest` pins what actually matters: a card with *no* guard leaks. Verified by mutation —
removing `carConsumeTouches()` fails the test, swapping it for the disabled clickable does not.

### Left alone, deliberately

`CarRestrictionDialog`'s card carries no touch guard, so tapping the dialog body dismisses it while
the driver may still be reading the reason. Pre-existing, and changing the dismissal behaviour of a
refusal dialog is a product call, not a refactor. Filed here rather than fixed.
