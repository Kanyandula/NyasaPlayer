# T27 - A disconnected controller in the field reaches us

- **Slice:** observability, playback lifecycle — story T24
- **Depends on:** T26 (the reporter class), T16 (closed; this is its tripwire)
- **Status:** Specced, not started
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest :automotive:testOemDebugUnitTest :app:testDebugUnitTest :app:assembleRelease :automotive:assembleOemRelease`
- **Design Reference:** T24 D7, D8; T16 Outcome; `docs/aaos-DESIGN.md` D65
- **Risk Tags:** playback lifecycle, both surfaces, module boundary, privacy
- **Affected Modules:** `:core:playback` (one hook), `:core:data` (one method), `:app` and
  `:automotive` (one override each)

## Problem

`BasePlayerStateCollector.onControllerLost()` logs whether the controller a command found was `null`
or `disconnected`. T16 closed on the grounds that nothing known produces `disconnected` in a live
process, and its Outcome says a `disconnected` line on a device reopens it. That line only exists in
the logcat of a device someone has plugged in, so the evidence T11 could never collect would be lost
again.

## Scope

- `BasePlayerStateCollector`: a new `protected open` hook with an empty default, alongside
  `onPlayerUnavailable`. Call it from `onControllerLost()` **only** when the controller was
  non-null and disconnected, after the existing `Log.w`. Name it for the state
  (e.g. `onControllerFoundDisconnected()`) and give it a KDoc that points at T16. The `null` case
  and the rebuild-failure `Log.w` stay logcat-only.
- The reporter from T26 gains one method that records a non-fatal: an exception whose message is a
  fixed string naming T16. No ids, no song or queue data, and no timestamp in the message. Firebase
  advises keeping unique values out of exception messages; they go in keys, and D8 rules them out
  here anyway. The `surface` key is already set, so the event carries it.
- The collector objects in `PlayerViewModel` and `AutomotivePlayerViewModel` override the hook and
  call the reporter. Each ViewModel injects the reporter; neither does anything else with it.
- Add a row to `docs/CRASH_REPORTING.md`: the event, when it fires, and what it carries.
- Update T16's Outcome paragraph on the tripwire so it says the line now reaches Crashlytics as a
  non-fatal, not just logcat.

## Out Of Scope

- Any other non-fatal (T24 Out Of Scope). Reporting `onPlayerUnavailable` or
  `onControllerConnectionFailed` is its own decision.
- Reopening T16. This ticket collects the evidence; a report that arrives is what reopens it.
- Changing the reconnect behaviour. The hook is observational, and the rebuild proceeds exactly as
  it does now.

## Acceptance Criteria

- Given a collector whose controller is disconnected, when a transport command runs, then the new
  hook fires once. Taps made while a reconnect is already in flight do not fire it again, the same
  single-attempt rule `onControllerLost` already enforces.
- Given a collector that never had a controller, when a transport command runs, then the hook does
  not fire.
- Given `:core:playback`'s dependencies, then they are unchanged. The collector and its tests never
  reference the reporter or Firebase.
- Given a release build where the hook fires, when the app next launches, then a non-fatal with the
  fixed T16 message appears in the dashboard carrying the right `surface`.
- Given `docs/CRASH_REPORTING.md`, then it lists this event.

## Notes

- **The tests.** `ReconnectingCollectorTest` already produces the disconnected case: a real
  `MediaSession` under Robolectric (T17), with `loseTheController()`. Count hook calls in its
  `TestCollector` the way `unavailableReports` counts `onPlayerUnavailable`. The first two criteria
  are JVM tests there. For the null case, don't call `connectController()`.
- **The device check has to be staged.** Nobody can produce `disconnected` on demand; that's the
  point of the tripwire. To prove the recording path once, make a local, uncommitted change that
  calls the hook from a button, run it on a signed release build, relaunch, and see the event.
  Then throw the change away. Record that it was done in the PR, not in a doc.
- **Delivery.** Crashlytics sends non-fatals with the next fatal or on the next launch, and keeps
  only the most recent eight between sends. One event per loss (the single-attempt rule) stays well
  inside that.
- **Why the ViewModels forward it** rather than the collector calling the reporter itself:
  `:core:playback` already depends on `:core:data` and could reach the reporter. But that would
  change the collector's constructor, which four places build (two ViewModels, two test classes),
  and it would put Firebase in the collector's tests. The hook changes neither.
