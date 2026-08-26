# T17 - The interesting half of playback can only be tested on a device

- **Slice:** test infrastructure
- **Depends on:** —
- **Status:** Implemented — no seam was needed
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest`
- **Design Reference:** `docs/aaos-DESIGN.md` D64; `docs/T13_VERIFICATION.md`, `docs/T10_VERIFICATION.md`
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

## Outcome

**No seam was built, and none is needed.** The ticket listed three options; the best one turned out to
be the one that changes no production code: stand up a real `MediaSession` over a `SimpleBasePlayer`
fake under Robolectric and connect a real `MediaController` to it. Design record D64.

`ConnectedTransportTest` is thirteen tests against a live controller, and the whole harness is
`MediaSession.Builder(context, player).build()`, `MediaController.Builder(context, session.token)
.buildAsync()`, and `shadowOf(Looper.getMainLooper()).idle()` to pump the connection. Two test
dependencies on `:core:playback` — `robolectric` and `androidx-junit`. The production diff is empty.

Five rows that three verification records had marked manual-only are now automated:

- T11's `isConnected == false` branch — every command refuses and reports, nothing reaches the player.
- The D62 rule that a connected controller whose guard declines stays silent, which T11's plan
  explicitly said could not be tested and told the implementer not to fake.
- T3's restore command, asserted from the bundle the session actually received: queue, index,
  position and repeat mode.
- `skipToQueueItem` in range and out of range.
- play, pause, seek, next, previous and repeat, all asserted at the player.

Gate: 304 tests, zero failures; detekt, lint, both automotive flavors and `:app:assembleDebug`.

## Notes for whoever extends it

`SimpleBasePlayer` renders exactly what `getState()` builds, so a static state leaves the media index
frozen and `hasPreviousMediaItem()` permanently false. The first `skipPrevious` test failed for that
reason and the test was wrong, not the code. The fake tracks its index and calls `invalidateState()`.

What it still cannot reach: anything requiring the real `PlaybackService`, since the harness replaces
it with a recording callback. Custom commands are asserted as *sent*, not as *handled* — the service
side stays covered by `MediaBrowseTreeTest` and the device passes.
