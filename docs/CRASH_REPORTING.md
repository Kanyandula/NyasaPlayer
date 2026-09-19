# Crash Reporting — What Crashlytics Sends

| | |
|---|---|
| **Applies to** | `:core:data`'s `firebase-crashlytics` SDK (T25), used by `:app` and `:automotive` |
| **Sources read** | Firebase pages 2026-09-14; the firebase-crashlytics 19.4.4 AAR 2026-09-15 — see each row below |

This is the inventory T24 D8 asks for: what this configuration sends, what it never sends, how
collection is switched, and how long Firebase keeps it.

## What is sent

The code sets one custom key, `surface` (see "Custom keys" below), and never calls `setUserId` or
`FirebaseCrashlytics.log`; everything else is the SDK's own defaults. Crashlytics pulls in Firebase
Sessions as a dependency, so its collection applies too.

### Per the privacy page (https://firebase.google.com/support/privacy, "Data processing information")

- RFC-4122 UUID used to deduplicate crashes
- Firebase installations ID (FID)
- Firebase session ID (random UUID tagging events to a session)
- Device specs: model name, CPU architecture, RAM, disk space
- Timestamp of the crash
- App bundle identifier and version number
- Device OS name and version
- Exception details, binary image information, runtime method/function names
- Screen rotation, proximity sensor status, app background state
- A boolean indicating whether the device was jailbroken/rooted
- Git commit SHA of the build (root path is the `$PROJECT_DIR` placeholder) — privacy page;
  observed in the release APK, `docs/T25_VERIFICATION.md`

### Per the Play data-disclosure page (https://firebase.google.com/docs/android/play-data-disclosure)

- Stack traces, at the point of a crash ("Crashlytics" section)
- Relevant application state at crash time ("Crashlytics" section)
- Relevant device metadata at crash time ("Crashlytics" section)
- Crashlytics installation UUID (identifies the install, used to measure affected users)
  ("Crashlytics" section)
- App metadata: package name, OS info, SDK version, network type ("Firebase sessions" section) —
  "network type" is this page's category; it is not a field in the Sessions 2.1.2 encoder read for
  "Observed in this build" below
- Device metadata: manufacturer and model (Firebase Sessions) ("Firebase sessions" section)
- Application metrics: app usage and session timing (Firebase Sessions) ("Firebase sessions" section)

The Play data-disclosure page also lists conditional collection — custom keys, logs, free-text user
IDs, custom non-fatal stack traces, and (with Analytics present) breadcrumb logs of user actions.
Of those, only the one custom key below applies here; for the rest see "What is never sent".

### Observed in this build (device settings via `docs/T25_VERIFICATION.md`; firebase-crashlytics 19.4.4 / firebase-sessions 2.1.2, read from the AAR)

- ANR reports: the device's cached settings carry `"collect_anrs":true` (`docs/T25_VERIFICATION.md`,
  "The car"), and Crashlytics 19.4.4 reports ANRs from `ApplicationExitInfo` on API 30+, not just
  crashes.
- Firebase Sessions events also carry a Firebase Installations auth token and process details
  (process name, pid, importance). The token is the installation's own token, not the signed-in
  user's Firebase Auth token — D8 still holds.

### Custom keys

| Key | Values | Set by |
|---|---|---|
| `surface` | `car` when the device has `PackageManager.FEATURE_AUTOMOTIVE`, otherwise `mobile` | `CrashReporter.start()` (`core/data/.../crash/CrashReporter.kt`), the first call in each app's `Application.onCreate` after Hilt field injection, once per process (T24 D6, T26) |

`surface` describes the device, not the APK: the phone app sideloaded onto a head unit reports
`car`. A crash before `start()` (during Hilt injection or content-provider start) carries no key.
The code sets no other key.

### Non-fatals the code records

| Event | When it fires | What it carries |
|---|---|---|
| `IllegalStateException("T16 tripwire: transport command found a disconnected controller")` | A transport command found a controller that was connected and is not any more — once per rebuild attempt, not once per tap. A `null` controller is ordinary and records nothing | The fixed message above, the stack at the point of the command, and the `surface` key. No ids, no song or queue data, no timestamp in the message (T24 D8) |

The message is a fixed string on purpose. Crashlytics groups by the stack, not the message — the
2026-09-19 staged check produced an issue titled after its calling frame, with the message as the
subtitle — so a unique value would not split the issue, but it would churn the title and make the
issue unreadable. Firebase advises against unique values in exception messages for that reason. `CrashReporter.reportControllerFoundDisconnected()` is the
only caller, reached through `BasePlayerStateCollector.onControllerFoundDisconnected()`, which each
surface's collector overrides (T27).

Non-fatals are delivered on the next launch or with the next fatal, and Crashlytics keeps only the
eight most recent between sends.

## What is never sent

- No `setUserId` — nothing identifies the signed-in user.
- No Firebase uid.
- No email.
- No song, queue or search text, in custom keys, logs, or exception messages.
- `FirebaseCrashlytics.log` is not called anywhere in the codebase; existing `Log.w`/`Log.e` calls
  stay in logcat only.
- No custom non-fatals beyond the one listed above: `recordException` is called from exactly one
  place, `CrashReporter.reportControllerFoundDisconnected()`.
- No breadcrumbs: Firebase Analytics is not a dependency of either app, and the SDK logs
  "Skipping logging Crashlytics event to Firebase, no Firebase Analytics".
- The Crashlytics installation UUID identifies the install, not the driver — on a shared head unit
  it does not distinguish who was driving.

## Where collection is switched, and how to check it

- Debug builds: `core/data/src/debug/AndroidManifest.xml` sets
  `firebase_crashlytics_collection_enabled` to `false`. Every debug variant of `:app` and
  `:automotive` consumes `:core:data`'s debug variant, so one file covers all of them.
- Release builds: no override is set, so collection defaults on.
- To check what the SDK is doing on a device:
  ```
  adb shell setprop log.tag.FirebaseCrashlytics DEBUG
  adb logcat -s FirebaseCrashlytics TRuntime.CctTransportBackend
  ```
  `FirebaseCrashlytics` logs the enqueue to DataTransport; the HTTP upload shows under tag
  `TRuntime.CctTransportBackend` (Info level, no `setprop` needed for it). In debug,
  `FirebaseCrashlytics` logs that automatic collection is disabled.
- While collection is off, a debug build still records its crashes on the device, unsent — a
  report file exists under `files/.crashlytics.v3/com.example.nyasaplayer/priority-reports/<id>`,
  observed at that path (`docs/T25_VERIFICATION.md`, "The car"), and no upload happens. Those stored
  reports are sent once a build with collection on runs in the same data directory, so uninstall the
  debug build before installing a release build signed with the same key on top of it
  (`docs/tickets/T25-crashlytics-in-core-data.md`, Notes — "Signing a release build to test with").
- Custom keys are recorded on the device whether or not collection is on, so a debug build shows
  them (file names from the 19.4.4 AAR's `FileStore` and `MetaDataStore`; seen on both emulators,
  `docs/T26_VERIFICATION.md`). `--user 10` is the car emulator's driver
  (`adb shell am get-current-user`); leave it out on a phone:
  ```
  adb shell run-as --user 10 com.example.nyasaplayer cat \
    files/.crashlytics.v3/com.example.nyasaplayer/open-sessions/<session-id>/keys
  ```
  After a crash the stored report under `priority-reports/` carries them as `customAttributes`.
- Reading the dashboard: custom keys are not a filter dimension in the Crashlytics reporting API
  (version, device, OS, form factor, error type, signals, issue, variant), so read `surface` from
  each event's keys. `topIssues` leaves out closed issues; `topVersions` and event queries still
  count their events, so an empty `topIssues` does not mean no crashes (owner, 2026-09-15).

## Retention

Per https://firebase.google.com/support/privacy (main data processing table, "Firebase
Crashlytics"): Crashlytics keeps crash stack traces, extracted minidump data, and associated
identifiers (including Crashlytics installation UUIDs and Firebase installation IDs) for 90 days
before starting removal from live and backup systems.

## The audience assumption

Release builds go to the owner's devices and testers only, so collection is on by default with no
consent UI (T24 D8). That assumption is load-bearing: shipping beyond the owner and testers — a
wider test group, an OEM partner, or a Play submission — reopens consent and a Play Data safety
declaration.

## Extending this file

Anything that adds to what Crashlytics sends — a key, a non-fatal, a new SDK — adds its row here in
the same change. T27 added the non-fatal above.
