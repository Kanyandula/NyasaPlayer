# T24 - Nothing that happens on a user's device reaches us

- **Slice:** observability, both apps
- **Depends on:** —
- **Status:** Filed, not specced
- **Verification Command:** `./gradlew :app:assembleRelease :automotive:assembleOemRelease`
- **Design Reference:** —
- **Risk Tags:** new SDK, privacy, car-app data policy, build config
- **Affected Modules:** `:app`, `:automotive` (and whichever module records the first non-fatal)

## Problem

NyasaPlayer has no crash reporting and no analytics. No module depends on Crashlytics, and the only
diagnostics are `android.util.Log` calls, which exist only in the logcat of a device someone has
plugged in.

That has already cost a question an answer. T11 could not settle whether the 2026-08-26 report came
from a null controller or a disconnected one, and T16 closed with a tripwire that can only fire in
front of a developer. A crash in the field is invisible in the same way.

## Scope

- Add `firebase-crashlytics` and its Gradle plugin to `:app` and `:automotive`. Both already apply
  `com.google.gms.google-services` and carry a (git-ignored) `google-services.json`, so the Firebase
  project wiring exists.
- Collect in release only; debug builds should not report.
- Decide what a **car** app may send. A head unit is shared, and a crash report can carry more than
  a stack trace — custom keys, logs, user ids. Settle what is attached, and whether the AAOS
  compliance notes (`docs/AAOS_COMPLIANCE.md`) or Play's automotive policies say anything about it,
  before wiring anything past the default.
- Decide whether the T16 tripwire becomes the first recorded non-fatal. If it does, it needs a way
  out of `:core:playback` that does not make that module depend on Crashlytics — the collector
  already exposes hooks to its ViewModels, which is the obvious seam.

## Out Of Scope

- Analytics. Different question, different consent.
- Recording non-fatals beyond the tripwire. Each one is a decision; the SDK is not a licence.

## Acceptance Criteria

- Given a release build that crashes, then the crash appears in the Firebase console for the right
  app.
- Given a debug build, then nothing is reported.
- Given the car app, then what it sends is written down and matches what the compliance notes allow.
