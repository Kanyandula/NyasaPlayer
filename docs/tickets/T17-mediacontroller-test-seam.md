# T17 - The interesting half of playback can only be tested on a device

- **Slice:** test infrastructure
- **Depends on:** —
- **Status:** Filed, not specced
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest`
- **Design Reference:** `docs/T13_VERIFICATION.md`, `docs/T10_VERIFICATION.md`
- **Risk Tags:** test-only, no production behaviour change
- **Affected Modules:** `:core:playback`, possibly `:app` and `:automotive` test source sets

## Problem

`MediaController` is `@DoNotMock` with a package-private constructor, so no unit test in this project
can construct one. Every consequence of that has been paid for in device time:

- T3 sent every claim about restore to an emulator.
- T10's `applyRestored` is testable only because it takes a value, not a controller.
- T13's transport tests cover the no-controller branch and nothing else.
- T11 cannot test its own headline condition — `isConnected == false` — at all.

Each ticket has written the same paragraph explaining why half its table is manual.

## Scope

- Evaluate the options: a thin interface over the operations the transport uses; a real `MediaSession`
  driven by Media3's `SimpleBasePlayer` under Robolectric; or Media3's own test artifacts.
- Note that `:core:playback` has JUnit and coroutines only — Robolectric lives in `:automotive`, so
  this may mean adding a test dependency.
- Whichever is chosen, it must let a test assert **what was sent** to the player, and simulate a
  controller that is connected and one that is not.
- Retrofit the existing manual-only rows: T13's connected-controller behaviour, T11's disconnected
  branch, T3's restore command.

## Out Of Scope

- Any production behaviour change. If a seam is chosen over a real session, production code changes
  shape but not behaviour, and that must be provable by the existing device records.

## Acceptance Criteria

- Given a connected fake player, then a test can assert the command a transport operation sent.
- Given a disconnected one, then a test can assert the refusal and the report.
- Given the existing suites, then they still pass unchanged.

## Notes

The cheapest of the four to justify and the one that pays back across all of them: it is the reason
three verification records in a row say "not unit-testable here". Worth doing before T14, whose
lifecycle logic would otherwise be device-only too.
