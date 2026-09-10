# T19 - Phone and email sign-in on the car auth screen

- **Slice:** A7 deferral
- **Depends on:** nothing in code; a product decision about head units without a SIM
- **Status:** Filed, not specced
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest detekt`
- **Design Reference:** `docs/aaos-DESIGN.md` D70; `docs/AAOS_SCREEN_CONTRACT.md` screen 1
- **Risk Tags:** text entry under restriction, SMS availability
- **Affected Modules:** `:automotive`, `:core:data` (`AuthRepository` already has both methods)

## Problem

The contract lists Google, phone and email sign-in on screen 1. Only Google is built.
`AuthRepository` already exposes `signInWithEmail` and `signUpWithEmail`, so the gap is UI plus a
restriction answer, not plumbing.

## Scope

- Both flows need text entry, which `NO_KEYBOARD` refuses while driving — so the screen has to
  decide what it shows when the vehicle is moving and neither field may be offered.
- Phone sign-in needs an SMS round trip on a head unit that may have no SIM. Decide what happens
  when it has none, before building the flow that assumes one.
- Retry on failure already works for Google — the button returns beneath the error message — and
  whatever is added has to behave the same way.

## Out Of Scope

- Password reset on the car. `sendPasswordResetEmail` exists; the phone is the right place for it.

## Acceptance Criteria

- Given the vehicle is parked, then a driver can sign in with email and with a phone number.
- Given the vehicle is moving, then no field, keypad or CTA that requires typing is rendered.
- Given a head unit with no SIM, then phone sign-in is not offered, rather than failing after the
  driver has entered a number.

## Notes

Google sign-in works today and covers the journey. This is completeness against the contract, not
a blocker.
