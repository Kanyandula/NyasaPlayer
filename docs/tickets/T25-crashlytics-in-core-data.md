# T25 - A release crash reaches Firebase; a debug crash does not

- **Slice:** observability, build config — story T24
- **Depends on:** —
- **Status:** Specced, not started
- **Verification Command:** `./gradlew :app:assembleDebug :app:assembleRelease :automotive:assembleOemDebug :automotive:assembleOemRelease :automotive:assemblePlaystoreDebug :automotive:assemblePlaystoreRelease`
- **Design Reference:** T24 D2, D3, D4, D5, D8
- **Risk Tags:** new SDK, build config, manifest merge, dependency resolution, privacy
- **Affected Modules:** `:core:data`, `:app` and `:automotive` (plugin line only), root
  `build.gradle.kts`, `gradle/libs.versions.toml`, `docs/`

## Problem

A crash on a tester's phone or head unit leaves no trace anywhere we can read. This ticket wires
Crashlytics so a release crash is sent, and makes sure a debug crash is not. This ticket adds no
Kotlin.

## Scope

- `gradle/libs.versions.toml`: versions `firebaseCrashlytics = "19.4.4"` and
  `firebaseCrashlyticsGradle = "3.0.8"`, the library `com.google.firebase:firebase-crashlytics`, and
  the plugin `com.google.firebase.crashlytics`. Use the non-`-ktx` artifact. The `-ktx` one was never
  released past the BoM 33 line.
- Root `build.gradle.kts`: add the plugin to the `apply false` block.
- `app/build.gradle.kts` and `automotive/build.gradle.kts`: add the plugin to `plugins {}`. Neither
  app gets anything else: no dependency, no code, no manifest change.
- `core/data/build.gradle.kts`: `implementation(libs.firebase.crashlytics)` next to the other
  Firebase lines.
- New `core/data/src/debug/AndroidManifest.xml`, containing
  `<meta-data android:name="firebase_crashlytics_collection_enabled" android:value="false" />`
  inside `<application>`, with a comment pointing at T24 D4.
- New `docs/CRASH_REPORTING.md`, the inventory D8 asks for. It covers:
  - what the SDK sends by default, taken from Firebase's current privacy and Play data-disclosure
    pages at implementation time, not from this ticket. Include Firebase Sessions, which
    Crashlytics pulls in.
  - what is never sent (D8's list)
  - where collection is switched, and how to check it (the Notes below)
  - retention, per Firebase's privacy page
- One line under `docs/AAOS_COMPLIANCE.md` → Distribution Tracks pointing at the inventory, so
  the car's data story has a home in the compliance notes.

## Out Of Scope

- The `surface` key and the startup class that sets it (T26).
- Any non-fatal (T27).
- Adopting the Firebase BoM, or moving any existing Firebase library (T24 D5).
- Minify, mapping upload, NDK.

## Acceptance Criteria

- Given `:app`'s `releaseRuntimeClasspath`, then `firebase-common` still resolves to 21.0.0.
  Crashlytics must not upgrade anything underneath `firestore-ktx` or `auth-ktx`.
- Given each release APK (`:app`, `:automotive` `oem` and `playstore`), then it carries a non-empty
  `com.google.firebase.crashlytics.mapping_file_id` string resource.
- Given the merged manifests, then every debug variant has
  `firebase_crashlytics_collection_enabled=false` and no release variant has it.
- Given the merged manifests, then Crashlytics and its dependencies added no activity, so MG-1,
  MG-2 and OG-3 read the same as before.
- Given a release build of the phone app that crashes, when it is relaunched, then the crash
  appears in the Crashlytics dashboard for `nyasamusic-5ed31`. The same holds for the `oem` car
  build on the AAOS emulator.
- Given a debug build that crashes, when it is relaunched, then Crashlytics debug logging reports
  collection disabled and nothing appears in the dashboard.
- Given `docs/CRASH_REPORTING.md`, then it lists everything the SDK sends in this configuration and
  nothing more.

## Notes

- **The build ID.** A launch crash with "The Crashlytics build ID is missing" means that app module
  lacks the plugin (T24 D3).
- **Signing a release build to test with.** Neither app has a release `signingConfig`, so
  `assembleRelease` produces an unsigned APK. For the checks above, sign it locally with
  `apksigner` and the debug keystore. **Uninstall the debug build first.** The two share a package,
  crashes a debug build recorded while collection was off are kept on the device, and they are
  sent once a build with collection on runs in the same data directory.
- **Forcing a crash without committing one.** Try `adb shell am crash com.example.nyasaplayer`, and
  confirm the result reaches the uncaught-exception handler as a Java exception (it shows up in
  the dashboard). If it doesn't, use a local, uncommitted `throw` behind a button.
- **Seeing what the SDK does.** Run `adb shell setprop log.tag.FirebaseCrashlytics DEBUG`, then
  `adb logcat -s FirebaseCrashlytics`. With collection on it logs the upload; in debug it logs that
  automatic collection is disabled.
- **Timing.** A fatal is sent on the next launch, not at the moment of the crash, so relaunch before
  looking. The dashboard can take a few minutes to show the first report.
- **The check for the first criterion:**
  `./gradlew :app:dependencyInsight --dependency com.google.firebase:firebase-common --configuration releaseRuntimeClasspath`.
  It read 21.0.0 on 2026-09-13, before this change.
