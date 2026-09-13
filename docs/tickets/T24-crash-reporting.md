# T24 - Nothing that happens on a user's device reaches us

- **Slice:** observability, both apps — a story, delivered by T25, T26 and T27
- **Depends on:** —
- **Status:** Story — specced; see Tickets
- **Verification Command:** each ticket's own; the story is done when all three pass
- **Design Reference:** Decisions below
- **Risk Tags:** new SDK, privacy, build config, both surfaces
- **Affected Modules:** `:core:data` (the SDK), `:app` and `:automotive` (one plugin line and one
  startup call each), `:core:playback` (one hook, no new dependency)

## Problem

NyasaPlayer has no crash reporting and no analytics. No module depends on Crashlytics, and the only
diagnostics are `android.util.Log` calls, which exist only in the logcat of a device someone has
plugged in.

That has already cost a question an answer. T11 could not settle whether the 2026-08-26 report came
from a null controller or a disconnected one, and T16 closed with a tripwire that can only fire in
front of a developer. A crash in the field is invisible in the same way.

## Decisions

Facts below were checked on 2026-09-13; the Notes say how.

**D1 — One Firebase app, one Crashlytics stream.** `:app` and `:automotive` both build
`applicationId = "com.example.nyasaplayer"` against Firebase project `nyasamusic-5ed31`, so they are
one Firebase Android client. That stays. The car does not get its own id; reports are told apart by
a custom key (D6).

**D2 — The SDK lives in `:core:data`.** Both apps depend on it, it already carries the Firebase
dependencies (auth, Firestore, Realtime Database), and it already owns the pattern D6 needs: a
`@Singleton` both `Application`s inject and start (`FirebaseSyncManager`, `CatalogSync`).
`:core:common` holds domain models, theme and Compose components and has no Firebase at all; putting
a backend SDK there would put it on the classpath of everything. As an `implementation` dependency,
Crashlytics types stay out of the apps' compile classpath, so every call goes through the
`:core:data` class.

**D3 — The Crashlytics Gradle plugin is the one per-app piece.** The SDK still refuses to start
without the build ID the plugin injects: `CrashlyticsCore.onPreExecute` throws
`IllegalStateException("The Crashlytics build ID is missing…")` when the
`com.google.firebase.crashlytics.mapping_file_id` resource is empty. The check runs before any
data-collection check, so a debug build with collection off dies at startup just the same. The
build ID is generated into the app's resources, so the plugin is applied in each application
module, as the setup guide does.

The SDK does honour a `com.google.firebase.crashlytics.RequireBuildId=false` boolean resource, which
would let `:core:data` skip the plugin entirely. Rejected: the setup guide never mentions it, and the
build ID is what ties a crash to its R8 mapping. Neither app minifies today; the day one does, the
plugin uploads the mapping on its own, and the flag would leave every stack trace obfuscated.

**D4 — Debug builds are switched off by a manifest flag in `:core:data`'s debug source set.**
`core/data/src/debug/AndroidManifest.xml` sets `firebase_crashlytics_collection_enabled` to
`false`. Both apps' debug variants consume the library's debug variant, so one file covers `debug`,
`oemDebug` and `playstoreDebug`.

The runtime alternative, `setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)`, is worse on three
counts. A `false` passed at runtime takes effect on the *next* run, so a debug build's first launch
reports. The override persists across launches and beats the manifest, so it would outlive a
reinstall of a different build type into the same data directory. And `:core:data` has no
`BuildConfig`. So nothing in this story calls `setCrashlyticsCollectionEnabled`.

**D5 — Versions: `firebase-crashlytics` 19.4.4, Crashlytics Gradle plugin 3.0.8.** The existing
Firebase libraries are pinned individually from the BoM 33 generation (auth and Firestore as `-ktx`
artifacts, Realtime Database as `firebase-database`), and `firebase-common` resolves to 21.0.0 in
`:app`'s release runtime classpath. Crashlytics 19.4.4 is
the last BoM 33 release (BoM 33.16.0), and its POM asks for `firebase-common` 21.0.0 exactly, so
nothing underneath the other libraries moves. Crashlytics 20.x belongs to BoM 34, which pulls
`firebase-common` 22.0.0 in underneath `-ktx` artifacts that generation stopped publishing. That
upgrade is the KTX migration, not this story. Plugin 3.0.8 is current and needs AGP 8.1+ and
google-services 4.4.1+; the project has 8.8.0 and 4.4.2.

**D6 — Car vs phone is a custom key, set once in shared code.** `surface` is `car` when
`PackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)` is true, otherwise `mobile`.
It is set from `Application.onCreate`, not an activity: the system media center starts the car's
`PlaybackService` without `AutomotiveActivity` ever running, and those crashes need the tag too.
The key describes the device, not the APK, so the phone app sideloaded onto a head unit reports
`car`. That is where it crashed, which is the question the key answers.

**D7 — The T16 tripwire becomes the first non-fatal, for `disconnected` only.** `null` is the
expected case (a tap before the first connection resolves) and stays in logcat. `disconnected` is
the state T16's Outcome says would reopen it, and nobody has produced it on a device. The way out of
`:core:playback` is a new `protected open` hook on `BasePlayerStateCollector`, in the same style as
`onPlayerUnavailable`. The collector objects in `PlayerViewModel` and `AutomotivePlayerViewModel`
forward it to the `:core:data` reporter. `:core:playback` gains no dependency and its tests never
touch Firebase.

**D8 — Privacy means writing down what is sent.** Release builds go to the owner's devices and
testers only, so collection is simply on in release, with no consent UI. What the apps send is
listed in `docs/CRASH_REPORTING.md`, which T25 creates and T26 and T27 extend. Nothing that
identifies a person is sent: no `setUserId`, no Firebase uid, no email, and no song, queue or search
text in keys, logs or exception messages. `FirebaseCrashlytics.log` is not used, so existing
`Log.w` lines stay in logcat. On a shared head unit the Crashlytics installation UUID identifies
the install, not the driver.

This decision assumes that audience. Shipping to anyone else, whether through Play, an OEM or a
wider test group, reopens consent and a Play Data safety declaration.

## Tickets

| Ticket | Delivers | Depends on |
|---|---|---|
| T25 | SDK in `:core:data`, plugin in both apps, quiet in debug, `docs/CRASH_REPORTING.md` | — |
| T26 | The `surface` key, set at startup in both apps | T25 |
| T27 | The T16 tripwire recorded as a non-fatal | T26 |

T25 alone produces crash reports. They just can't be filtered by surface until T26 lands.

## Scope

What the three tickets deliver, together: crash reporting for release builds of both apps, one
stream, filterable by surface, silent in debug, with everything sent written down.

## Out Of Scope

- Analytics. Different question, different consent.
- Recording non-fatals beyond the tripwire. Each one is a decision; the SDK is not a licence.
- Routing `Log.w`/`Log.e` into Crashlytics logs (the `CrashlyticsTree` pattern in
  `~/StudioProjects/Nyasa`). It would send log text that has never been reviewed under D8.
- A second Firebase app or a car `applicationId` (D1).
- Consent UI and opt-in reporting, until the audience in D8 widens.
- R8 and mapping upload. Nothing to do until `isMinifyEnabled` flips; the plugin handles it then (D3).
- NDK crash reporting. The project has no native code.
- Firebase BoM 34 and the KTX migration (D5).

## Acceptance Criteria

- Given a release build of either app that crashes, when it is relaunched, then the crash appears
  in the one Crashlytics stream for `nyasamusic-5ed31` and can be filtered to phone or car by
  `surface`.
- Given a debug build that crashes, then nothing is sent.
- Given either app, then everything it sends is listed in `docs/CRASH_REPORTING.md`, and nothing
  sent identifies the signed-in user.
- Given a controller found `disconnected` in the field, then it arrives as a non-fatal carrying
  its `surface`.

## Notes

How the facts behind the Decisions were checked, 2026-09-13:

- D3: `CrashlyticsCore.java` and `CommonUtils.java` on `firebase-android-sdk` `main`, which is
  newer than 19.4.4. The "build ID is missing" startup crash is an old onboarding failure, so the
  check is older than 19.4.4 too. T25 applies the plugin either way.
- D4: Firebase's `setCrashlyticsCollectionEnabled` reference (a `false` override applies on the
  next run and persists) and the Crashlytics customize-reports guide (the manifest flag; reports
  made while collection is off are stored on the device).
- D5: `./gradlew :app:dependencyInsight --dependency com.google.firebase:firebase-common
  --configuration releaseRuntimeClasspath`, which gives 21.0.0. The BoM 34.0.0 release notes list
  the 33.16.0 → 34.0.0 mapping. The `firebase-crashlytics-19.4.4.pom` on Google Maven.
- The reference app `~/StudioProjects/Nyasa` uses BoM 33.8.0 with `firebase-crashlytics-ktx` and
  plugin 3.0.3 on the buildscript classpath. It is a useful check on versions, but don't copy its
  debug behaviour: it never switches collection off, so its debug builds report.
- `docs/AAOS_COMPLIANCE.md` says nothing about telemetry. The `oem` track ships through OEM
  partnership or direct install (Distribution Tracks; `docs/AAOS_PRD.md` §3.2–3.3), so there is no
  Play data policy to satisfy until the `playstore` flavor is submitted.
