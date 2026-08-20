# AAOS Compliance Contract

| | |
|---|---|
| **Applies to** | `:automotive` AAOS upgrade |
| **Status** | Binding for A1 and later AAOS UI phases |
| **Date** | 2026-08-03 |
| **Primary PRD** | `docs/AAOS_PRD.md` |
| **Notion** | https://app.notion.com/p/3b0728b1385d81afbfade86ccddd1eeb |

This document makes the AAOS compliance split executable. It does not replace the PRD; it
turns PRD §8 into variant-specific gates that can be checked against built APKs and emulator
smoke tests.

## External Rules Interpreted

- Android car app quality: Play media apps cannot provide app-owned browse/playback activities
  except parked setup, settings and sign-in flows (car-quality `PE-1`). They also require no
  auto-scrolling text (car-quality `ST-1`), voice commands (`VC-1`), nonfunctional disabled
  controls (`GB-1`), no autoplay (`MA-1`) and strict animation limits (`SA-1`).

> **Identifier namespaces.** `PE-*`, `ST-*`, `VC-*`, `GB-*`, `MA-*` and `SA-*` above are
> **Google's** car-quality rule IDs and are always written with the `car-quality` prefix.
> Gate IDs defined *by this document* are `OG-*`, `MG-*` and `HR-*`. Google's `ST-1` (no
> auto-scrolling text) is unrelated to this document's host-render tests, which is why those
> are `HR-*` and not `ST-*`.
- Android Automotive media apps: the Play media path is rendered from `MediaBrowserService` /
  `MediaLibraryService` plus `MediaSession`; app-owned sign-in/settings are optional parked
  flows.
- `CarUxRestrictions`: if `isRequiresDistractionOptimization()` is true, the foreground
  activity must be declared distraction optimized and must react to active restrictions such as
  `NO_KEYBOARD`, `NO_SETUP`, content depth and item caps.
- Driver Distraction Guidelines: AAOS checks the manifest declaration before allowing an
  activity to run in a restricted state. The declaration is a safety assertion, not styling.

References:

- https://developer.android.com/docs/quality-guidelines/car-app-quality
- https://developer.android.com/training/cars/media/automotive-os
- https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictions
- https://source.android.com/docs/automotive/driver_distraction/guidelines

## Distribution Tracks

| Track | Shipped UI | Distribution posture | Compliance target |
|---|---|---|---|
| `oem` | Custom Compose launcher plus `PlaybackService` | OEM partnership / direct install | Must satisfy AAOS driver-distraction behavior for every driving-reachable activity |
| `playstore` | `PlaybackService` only; no custom launcher | Future Play AAOS media submission | Must satisfy Play media category rules |

The `oem` flavor is intentionally not the Play media submission artifact. The `playstore`
flavor exists so that a future Play path remains viable without contaminating the product UI.

## `oem` Gates

Run these against the merged `oem` APK manifest and emulator behavior.

| Gate | Assertion |
|---|---|
| OG-1 | `AutomotiveActivity` is the launcher activity for the `oem` flavor |
| OG-2 | `AutomotiveActivity` declares `<meta-data android:name="distractionOptimized" android:value="true" />` |
| OG-3 | Every other activity reachable while driving carries the same declaration |
| OG-4 | Standalone parked-only activities, if any, do not declare `distractionOptimized`; parked-only screens inside `AutomotiveActivity` are refused by the runtime gate |
| OG-5 | Runtime state comes from `CarUxRestrictions`, including `isRequiresDistractionOptimization()`, `NO_KEYBOARD`, `NO_SETUP`, max depth and max item count |
| OG-6 | Entering a restricted location while driving is refused with an explanation |
| OG-7 | Transitioning to driving while already in a restricted location evicts to the current tab root |
| OG-8 | Playback transport, seek, tab switch and queue skip-to remain available while driving |
| OG-9 | Queue remove/clear and download delete/remove actions are parked-only, and the queue list truncates to the reported item cap while driving (reorder does not ship, D26) |
| OG-10 | The app does not request `RECORD_AUDIO` and does not implement an in-app audio recorder for voice search |
| OG-11 | Decorative animation freezes while driving and is disabled when platform animator duration scale is `0` |

OG-2 should land only after the behavior behind it is true. Adding the metadata before A1's
restriction, touch-target and contrast gates pass would make a false safety claim to the
platform.

## `playstore` Gates

Run these against the merged `playstore` APK manifest, not source manifests. Manifest merging
pulls declarations from `:core:playback` and libraries.

| Gate | Assertion |
|---|---|
| MG-1 | Zero activities declare `android.intent.category.LAUNCHER` |
| MG-2 | Zero exported activities exist except explicit parked setup/sign-in/settings allow-list entries |
| MG-3 | `PlaybackService` / media browser service is present and exported for the car host |
| MG-4 | Settings, if present, is reachable with `android.intent.action.APPLICATION_PREFERENCES` |
| MG-5 | `<uses-feature android:name="android.hardware.type.automotive" android:required="true" />` is present |
| MG-6 | `automotive_app_desc.xml` declares `<uses name="media" />` |
| MG-7 | No custom browse, playback, queue, search or downloads activity is shipped |

## Host-Render Smoke Tests

These are manual or emulator smoke tests for the `playstore` path. They run before any Play
submission decision, not on every commit.

| Test | Passes when |
|---|---|
| HR-1 | The app appears in the AAOS media source picker |
| HR-2 | The host renders the browse root and one child level |
| HR-3 | Selecting a playable item starts playback |
| HR-4 | Now Playing metadata matches title, artist and artwork |
| HR-5 | Queue is populated and skip-to works |
| HR-6 | Search returns results through `onSearch` / `onGetSearchResult` |
| HR-7 | Assistant voice playback works end to end |
| HR-8 | Custom actions such as like/unlike appear and reflect state |

Now Playing may render on `FLAG_SECURE` distant-display surfaces, so screenshot verification is
not always available. Use `dumpsys media_session` for playback state and custom actions.

## Source Layout Contract

| Source set | Manifest responsibility |
|---|---|
| `automotive/src/main` | Shared permissions, app metadata, automotive descriptor, no launcher after A1 |
| `automotive/src/oem` | Adds `AutomotiveActivity`, launcher filter and `distractionOptimized=true` metadata |
| `automotive/src/playstore` | Adds no launcher activity |
| `:core:playback` | Contributes `PlaybackService` through manifest merging |

The verification commands in
`docs/superpowers/plans/2026-08-02-aaos-foundation-restrictions.md` Task 13 are the first
implementation of these gates.
