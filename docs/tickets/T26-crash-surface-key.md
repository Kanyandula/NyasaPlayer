# T26 - A crash report says whether it came from the car or the phone

- **Slice:** observability, both surfaces — story T24
- **Depends on:** T25
- **Status:** Implemented; verified on both surfaces and in the dashboard, criterion 4 amended — see
  Outcome and `docs/T26_VERIFICATION.md`
- **Verification Command:** `./gradlew detekt :app:assembleRelease :automotive:assembleOemRelease :automotive:assemblePlaystoreRelease :app:lintDebug :core:data:lintDebug :automotive:lintOemDebug`
- **Design Reference:** T24 D1, D2, D6, D8
- **Risk Tags:** app startup, both surfaces, privacy
- **Affected Modules:** `:core:data` (new class), `:app` and `:automotive` (`Application.onCreate`)

## Problem

After T25 both apps' crashes land in one stream (T24 D1), with nothing to tell them apart except
the device model. Head-unit model names are
chosen by OEMs, and AAOS emulator images use generic ones, so that is not a filter anyone can rely
on.

## Scope

- A `@Singleton` reporter class in `:core:data` whose `start()` sets the Crashlytics custom key
  `surface` as T24 D6 defines it.
- `FirebaseCrashlytics.getInstance()` is called inside `start()`, not in the constructor or a
  property initializer. T27 injects this class into two ViewModels, and constructing one must not
  touch Firebase.
- `NyasaPlayerApplication` and `AutomotiveApplication` each inject it and call `start()` first in
  `onCreate`: on the phone ahead of `firebaseSyncManager.start()`, on the car ahead of and outside
  the `isAuthenticated` gate around `catalogSync.start()`. A crash in either sync then already
  carries the key, and so does a signed-out car.
- Add a `surface` row to `docs/CRASH_REPORTING.md`.

## Out Of Scope

- A key for the `oem`/`playstore` flavor. Add it when `playstore` is actually distributed. Until
  then every car report is `oem`.
- `setUserId`, or any key that identifies the user (T24 D8).
- Setting keys anywhere other than startup. More state keys are more decisions under D8.

## Acceptance Criteria

- Given the phone app crashes, then its dashboard event carries `surface=mobile`.
- Given the `oem` car app crashes on the AAOS emulator, then its event carries `surface=car`.
- Given the car's `PlaybackService` started by the system media center, with `AutomotiveActivity`
  never launched, when the process crashes, then the event still carries `surface=car`.
- Given the dashboard, then each event shows `surface` among its keys. (Amended: this first said
  issues could be searched and events filtered by `surface`. The Crashlytics reporting API has no
  custom-key filter; see Outcome.)
- Given `docs/CRASH_REPORTING.md`, then it lists `surface` and its two values.

## Notes

- **Why no unit test.** The logic is one `if` on one platform call. The emulator checks above are
  the test. Add one if `start()` grows a second decision. No JVM test reaches it: `:automotive`'s
  Robolectric tests run on a plain `android.app.Application`
  (`automotive/src/test/resources/robolectric.properties`) and `:app` has no Robolectric tests.
  (`:app`'s instrumented test does run the real `Application`, on a device, where Firebase exists.)
- **Checking the key without the dashboard.** Custom keys are recorded on the device whether or not
  collection is on, so a debug build shows `surface` in the open session and in a stored crash
  report. That covers both surfaces and the media-template start without a release install. The
  command is in `docs/CRASH_REPORTING.md` → "Where collection is switched, and how to check it".
- **Before a release crash**, clear stored debug reports: a build with collection on uploads them.
  Uninstall the debug build, or delete `priority-reports/` with `run-as`.
- **The phone** needs an AVD with room to install; `docs/T25_VERIFICATION.md` → "The phone" records
  one that had none. The key is set before sign-in matters, so no signed-in account is needed.
- **Starting the car without its activity.** Force-stop the app, then
  `adb shell am start --user 10 -a android.car.intent.action.MEDIA_TEMPLATE -e android.car.intent.extra.MEDIA_COMPONENT com.example.nyasaplayer/com.example.nyasaplayer.core.playback.PlaybackService`.
  `com.android.car.media` binds `PlaybackService`; confirm it in `dumpsys activity services
  com.example.nyasaplayer`, and that `AutomotiveActivity` is absent from `dumpsys activity
  activities`.
- **Forcing the crash.** `adb shell am crash <pid>`. On API 35 `am crash <package>` returns 0 and
  does nothing; the pid form works on both images.

## Outcome

`CrashReporter` (`core/data/.../crash/CrashReporter.kt`) sets `surface` from each app's
`Application.onCreate`: first on the phone, and on the car ahead of and outside the sign-in gate. It
was verified on-device for all three paths and, for one car release crash, in the dashboard (the
owner read `customKeys: surface: car` on the event). Evidence: `docs/T26_VERIFICATION.md`.

- **Criterion 4 was wrong.** The owner found that the Crashlytics reporting API filters by version,
  device, OS, form factor, error type, signals, issue and variant, with no custom-key dimension.
  `surface` is read from each event's keys, not filtered on. Splitting car from phone at the issue
  level would need BigQuery export; nothing here needs that yet.
- **What the key can't cover.** Hilt injects the `Application`'s fields inside `super.onCreate()`,
  before `start()`, and content providers start earlier still. A crash there, or an ANR in a process
  that hung before `start()`, carries no `surface`.
- **Clearing stored debug reports.** The car held four from debug runs; with the owner's approval
  they were deleted from `priority-reports/` rather than uninstalling, which kept the car's sign-in.
