# Crash Reporting — What Crashlytics Sends

- **Applies to** `:core:data`'s `firebase-crashlytics` SDK (T25), used by `:app` and `:automotive`
- **Status** Accurate for the default configuration T25 ships: no custom keys, no logging, no user ID
- **Sources read** 2026-09-14 — see each row below

This is the inventory T24 D8 asks for: what this configuration sends, what it never sends, how
collection is switched, and how long Firebase keeps it.

## What is sent

Nothing in this codebase calls `setCustomKey`, `setUserId` or `FirebaseCrashlytics.log`, so only the
SDK's own defaults apply. Crashlytics pulls in Firebase Sessions as a dependency, so its collection
applies too.

| Item | Source |
|---|---|
| Stack traces, at the point of a crash | https://firebase.google.com/docs/android/play-data-disclosure ("Crashlytics" section) |
| Relevant application state at crash time | https://firebase.google.com/docs/android/play-data-disclosure ("Crashlytics" section) |
| Relevant device metadata at crash time | https://firebase.google.com/docs/android/play-data-disclosure ("Crashlytics" section) |
| Crashlytics installation UUID (identifies the install, used to measure affected users) | https://firebase.google.com/docs/android/play-data-disclosure ("Crashlytics" section) |
| RFC-4122 UUID used to deduplicate crashes | https://firebase.google.com/support/privacy ("Data processing information") |
| Firebase installations ID (FID) | https://firebase.google.com/support/privacy ("Data processing information") |
| Firebase session ID (random UUID tagging events to a session) | https://firebase.google.com/support/privacy ("Data processing information") |
| Device specs: model name, CPU architecture, RAM, disk space | https://firebase.google.com/support/privacy ("Data processing information") |
| Timestamp of the crash | https://firebase.google.com/support/privacy ("Data processing information") |
| App bundle identifier and version number | https://firebase.google.com/support/privacy ("Data processing information") |
| Device OS name and version | https://firebase.google.com/support/privacy ("Data processing information") |
| Exception details, binary image information, runtime method/function names | https://firebase.google.com/support/privacy ("Data processing information") |
| Screen rotation, proximity sensor status, app background state | https://firebase.google.com/support/privacy ("Data processing information") |
| A boolean indicating whether the device was jailbroken/rooted | https://firebase.google.com/support/privacy ("Data processing information") |
| Version-control info: the git commit SHA of the build, and a placeholder root path (`$PROJECT_DIR`), not a real filesystem path — from `META-INF/version-control-info.textproto`, which AGP writes into every release APK and the Crashlytics plugin copies into the `com.google.firebase.crashlytics.version_control_info` string resource | https://firebase.google.com/support/privacy ("Data processing information") |
| App metadata: package name, OS info, SDK version, network type (Firebase Sessions) | https://firebase.google.com/docs/android/play-data-disclosure ("Firebase sessions" section) |
| Device metadata: manufacturer and model (Firebase Sessions) | https://firebase.google.com/docs/android/play-data-disclosure ("Firebase sessions" section) |
| Application metrics: app usage and session timing (Firebase Sessions) | https://firebase.google.com/docs/android/play-data-disclosure ("Firebase sessions" section) |

The Play data-disclosure page also lists conditional collection — custom keys, logs, free-text user
IDs, custom non-fatal stack traces, and (with Analytics present) breadcrumb logs of user actions.
None of that applies here: see "What is never sent" below.

## What is never sent

- No `setUserId` — nothing identifies the signed-in user.
- No Firebase uid.
- No email.
- No song, queue or search text, in custom keys, logs, or exception messages.
- `FirebaseCrashlytics.log` is not called anywhere in the codebase; existing `Log.w`/`Log.e` calls
  stay in logcat only.
- The Crashlytics installation UUID identifies the install, not the driver — on a shared head unit
  it does not distinguish who was driving.

## Where collection is switched, and how to check it

- Debug builds: `core/data/src/debug/AndroidManifest.xml` sets
  `firebase_crashlytics_collection_enabled` to `false`. Every debug variant of `:app` and
  `:automotive` consumes `:core:data`'s debug source set, so one file covers all of them.
- Release builds: no override is set, so collection defaults on.
- To check what the SDK is doing on a device:
  ```
  adb shell setprop log.tag.FirebaseCrashlytics DEBUG
  adb logcat -s FirebaseCrashlytics
  ```
  With collection on, it logs the upload; in debug, it logs that automatic collection is disabled.
- While collection is off, a debug build still records its crashes on the device, unsent — a
  report file exists under `files/.crashlytics.v3/com.example.nyasaplayer/priority-reports/<id>`,
  and no upload happens. Those stored reports are sent once a build with collection on runs in the
  same data directory, so uninstall the debug build before installing a release build signed with
  the same key on top of it (`docs/tickets/T25-crashlytics-in-core-data.md`, Notes — "Signing a
  release build to test with").

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

T26 (the `surface` key) and T27 (the `disconnected` non-fatal) extend this inventory as they add
what they send.
