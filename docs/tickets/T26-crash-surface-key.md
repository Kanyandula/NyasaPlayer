# T26 - A crash report says whether it came from the car or the phone

- **Slice:** observability, both surfaces — story T24
- **Depends on:** T25
- **Status:** Specced, not started
- **Verification Command:** `./gradlew detekt :app:assembleRelease :automotive:assembleOemRelease :automotive:assemblePlaystoreRelease`
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
  `onCreate`, ahead of `firebaseSyncManager.start()` and outside the car's `isAuthenticated` gate,
  so a crash in either sync already carries the key.
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
- Given the dashboard, then issues can be searched by `surface`, and an issue's events can be
  filtered by it.
- Given `docs/CRASH_REPORTING.md`, then it lists `surface` and its two values.

## Notes

- **Why no unit test.** The logic is one `if` on one platform call. The emulator checks above are
  the test. Add one if `start()` grows a second decision.
- **Starting the car without its activity.** Force-stop the app, open the OEM media template
  (`com.android.car.media`; see `docs/AAOS_T3_VERIFICATION.md` → The OEM template surface), pick
  NyasaPlayer as the source and press play. Confirm the session in `dumpsys media_session`,
  anchoring on the current `ownerPid` as that record's Observations explain. Confirm
  `AutomotiveActivity` is absent from `dumpsys activity activities`. Then force the crash as in
  T25's Notes.
