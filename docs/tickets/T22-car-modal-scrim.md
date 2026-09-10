# T22 - Three copies of the same modal scrim

- **Slice:** A7 review finding — pre-existing duplication
- **Depends on:** nothing
- **Status:** Filed, not specced
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
