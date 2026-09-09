# T18 - Screen 2: what does the PIN protect, and where is it stored?

- **Slice:** A7 deferral — the screen it left unbuilt
- **Depends on:** a storage and lockout decision, not on code
- **Status:** Filed, not specced
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest detekt`
- **Design Reference:** `docs/aaos-DESIGN.md` D67; `docs/AAOS_SCREEN_CONTRACT.md` screen 2
- **Risk Tags:** security, credential storage, first-run flow
- **Affected Modules:** `:automotive`, possibly `:core:data`

## Problem

US-3 is P-priority and the contract describes what `CarPinOptInScreen` looks like: PIN dots, a
numeric keypad, Enable PIN, Not now. Nothing anywhere says what the PIN *protects*. A7 shipped no
PIN affordance at all rather than a disabled one, because a disabled control is a promise and there
was nothing behind this one.

## Scope

Answer the question first, in a spec, then build:

- What the PIN gates. The candidate is a local re-auth gate: the PIN unlocks the app on this head
  unit so a passenger cannot see or change the account.
- Where the salted hash lives — `EncryptedSharedPreferences` or the keystore — and what happens
  after N failures.
- Where the opt-in is offered: after first sign-in, per the contract.
- `NO_SETUP` refuses the screen, through the existing gate. It is a parked-only surface like every
  other A7 one.

## Out Of Scope

- Anything server-side. The app has no PIN concept in Firestore and inventing one is a backend
  change.
- Reusing the PIN as a second factor for Firebase auth.

## Acceptance Criteria

- Given a signed-in driver who enabled a PIN, when the app is reopened, then it asks for the PIN
  before showing the account.
- Given N wrong entries, then the documented lockout applies.
- Given the vehicle is moving, then the screen is refused with the gate's written reason.

## Notes

The cheapest correct outcome is still "drop US-3". File this closed with a reason if that is the
call — what must not happen is a PIN that gates nothing.
