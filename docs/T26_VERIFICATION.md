# T26 — verification record

Covers the device pass for `docs/tickets/T26-crash-surface-key.md`.

- **Date:** 2026-09-15
- **Branch:** `ek/t26-crash-surface-key`, code at `68e896e`
- **Phone AVD:** `Pixel_9_Pro_Fold_API_35` (`sdk_gphone64_x86_64`, no `android.hardware.type.automotive`)
- **Car AVD:** `AAOS_AOSP_33_userdebug`, driver user 10, `oem` build

## Gate

`./gradlew detekt :app:assembleDebug :app:assembleRelease :automotive:assembleOemDebug
:automotive:assembleOemRelease :automotive:assemblePlaystoreRelease test :app:lintDebug
:core:data:lintDebug :automotive:lintOemDebug` — BUILD SUCCESSFUL.

## Debug builds: the key on the device

Read with `run-as` (`--user 10` on the car) from the Crashlytics files: the open session's `keys`,
and the stored report's `customAttributes` after `am crash <pid>`.

| Path | Open session `keys` | Stored crash report |
|---|---|---|
| Phone, launcher activity | `{"surface":"mobile"}` | `"customAttributes":[{"key":"surface","value":"mobile"}]`, `CrashedByAdbException` |
| Car, `AutomotiveActivity` launched | `{"surface":"car"}` | `"customAttributes":[{"key":"surface","value":"car"}]`, `CrashedByAdbException` |
| Car, started by the OEM media template | `{"surface":"car"}` | `"customAttributes":[{"key":"surface","value":"car"}]`, `CrashedByAdbException` |

The media-template start: force-stopped (no pid), then
`am start --user 10 -a android.car.intent.action.MEDIA_TEMPLATE -e android.car.intent.extra.MEDIA_COMPONENT com.example.nyasaplayer/com.example.nyasaplayer.core.playback.PlaybackService`.
`dumpsys activity services` showed `PlaybackService` bound by `com.android.car.media` through the
`android.media.browse.MediaBrowserService` intent, and `dumpsys activity activities` held zero
`AutomotiveActivity` records, checked twice up to the moment of the crash.

On the API 35 phone, `am crash com.example.nyasaplayer` returned 0 and did nothing; `am crash <pid>`
worked. The car (API 33) takes either.

## Release: the key reaches Firebase

The car held four stored debug reports: T25's debug crash, an ANR, and the two car crashes above.
With the owner's approval, those four files were deleted from `priority-reports/` (the rest of the
app's data, including sign-in, kept), and the signed `oem` release installed in place. One crash by
pid; exactly one report was sent:

```
09-15 19:01:49.697 D/FirebaseCrashlytics(28702): Handling uncaught exception "android.app.RemoteServiceException$CrashedByAdbException: shell-induced crash" from thread main
09-15 19:01:50.351 D/FirebaseCrashlytics(28702): Crashlytics report successfully enqueued to DataTransport: 6AA987FD02B20001701E48B200A6EB51
09-15 19:02:03.092 I/TRuntime.CctTransportBackend(28803): Making request to: https://crashlyticsreports-pa.googleapis.com/v1/firelog/legacy/batchlog
09-15 19:02:05.294 I/TRuntime.CctTransportBackend(28803): Status Code: 200
```

The debug build was put back afterwards.

## The dashboard

Checked by the owner through the Crashlytics reporting API, 2026-09-15:

- The event: `CrashedByAdbException: shell-induced crash`, `eventTime` 2026-09-15T18:01:49Z
  (19:01:49 IST), `customKeys: surface: car`, v1.0 (1), emulator, Android 13. Issue
  `60b276352ba43b1763298aa997723cd1`.
- T25's event is on the same issue at 19:26:05 IST on 2026-09-14, with no `customKeys` (that build
  predates T26).
- `topVersions` over both IST days returns one group, 1.0 (1), `eventsCount: 2`: those two events
  are everything, so no debug crash reached the dashboard.
- The key can't be filtered on: the reporting API has no custom-key dimension. The owner read it
  from the event's keys. The ticket's fourth criterion is amended to match.
- `topIssues` for the same window is empty because the issue is closed and that report leaves
  closed issues out.

## Also seen

The ANR in the car's stored reports was recorded at 18:49:42 by the T25 build, while a Gradle build
and three emulators were running on this host. Its `customAttributes` is empty because that build
predates T26. It is an emulator-load ANR, not investigated, and it shows ANRs are captured.
