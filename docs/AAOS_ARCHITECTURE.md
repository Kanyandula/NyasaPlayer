# NyasaPlayer — Android Automotive OS (AAOS) Architecture

> Reference for the AAOS variant of NyasaPlayer. Updated 2026-08-03 for
> `docs/AAOS_PRD.md` v1.0. The 2026-04-23 **Option B (Template Path)** decision is
> now historical; it is retained in §6 because it explains the preserved Play path.

---

## Table of Contents

1. [Overview](#overview)
2. [Module Boundaries](#module-boundaries)
3. [MediaLibrarySession — shared AAOS media surface](#medialibrarysession--shared-aaos-media-surface)
4. [State Synchronization](#state-synchronization)
5. [Variant-specific UI Surfaces (`:automotive`)](#variant-specific-ui-surfaces-automotive)
6. [Historical Decision: Option B — Template Path](#historical-decision-option-b--template-path)
7. [Firebase Backend Compatibility](#firebase-backend-compatibility)
8. [Implementation Roadmap](#implementation-roadmap)

---

## Overview

NyasaPlayer AAOS shares the domain, data, and playback layers with the mobile app. The
current product decision is **dual-track by build variant**:

- **`oem`** is the product surface: a custom 20-screen Compose launcher in `:automotive`,
  distributed through OEM partnership or direct install.
- **`playstore`** is the preserved future Play media path: no launcher activity; browse,
  search and playback are host-rendered from `PlaybackService` / `MediaLibraryService`.

`PlaybackService` remains load-bearing in both tracks. It powers the Play media-template
surface, Assistant voice playback/search, media-session controls, queue persistence and
cross-device resume.

**Target display:** 1920×1080 landscape design canvas, adapted with dp.
**Min SDK:** 29.
**`<uses name="media" />`:** required for the preserved Play Store AAOS media variant.

---

## Module Boundaries

```
:core:common  <--  :core:data  <--  :core:playback  -+-- :app (mobile)
                                                     +-- :automotive (AAOS: OEM launcher + Play media variant)
```

| Module | Package | Responsibility |
|---|---|---|
| `:core:common` | `core.common` | Domain models, theme, shared UI primitives, NetworkMonitor |
| `:core:data` | `core.data` | Repositories, Room, Firebase sync, preferences DataStore |
| `:core:playback` | `core.playback` | `PlaybackService` (`MediaLibraryService`), `MediaBrowseTree`, `PlaybackQueueManager`, `PlaybackStatePersistence`, `SongMediaItemMapper`, `PlaybackCommands` |
| `:app` | `com.example.nyasaplayer` | Mobile-only screens, ViewModels, navigation, `MediaController` client |
| `:automotive` | `com.example.nyasaplayer.auto` | AAOS app shell. `oem` adds the custom launcher; `playstore` removes it and relies on `PlaybackService`. Parked-only setup/settings/sign-in flows stay custom where allowed |

### `:automotive` build.gradle (key)

```kotlin
android {
    namespace = "com.example.nyasaplayer.auto"
    defaultConfig { applicationId = "com.example.nyasaplayer"; minSdk = 29 }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:playback"))  // PlaybackService merges from here
    implementation(project(":core:data"))       // AuthRepository + AudioQualityPreference
    implementation(project(":core:common"))     // Theme + NyasaIcons
    // No androidx.car.app — we're a MediaLibraryService app, not a Car App Library app.
}
```

---

## MediaLibrarySession — shared AAOS media surface

The AAOS media API surface is the `MediaLibrarySession.Callback` installed on
`PlaybackService`. In the `playstore` flavor, the OEM host calls these methods and what
we return is the UI. In the `oem` flavor, the custom Compose launcher draws the product UI,
but the same session remains the contract for Assistant, system voice search, media controls
and external clients.

### Browse tree

```
ROOT (grid)
 +-- "Recently Played"   (LIST  — user-specific, from Firestore)
 +-- "Liked Songs"        (LIST  — user-specific, from Firestore)   ← added in Option-B migration
 +-- "Genres"             (GRID  — from Room GenreDao)
 |    +-- "Electronic"
 |    +-- ...
 +-- "Artists"            (GRID  — from Room ArtistDao)
 |    +-- "Jimi Hendrix"
 |    +-- ...
 +-- "All Songs"          (LIST  — from Room SongDao, sorted by popularity)
```

Style hints come from
`androidx.media.utils.MediaConstants.EXTRA_BROWSABLE_STYLE_HINT_CATEGORY_{GRID,LIST}_ITEM`
in the child `MediaItem` extras. Depth = Root → Category → Song (3 taps) — within the
AAOS 6-tap limit.

### Callbacks implemented in `PlaybackService`

| Callback | Purpose |
|---|---|
| `onGetLibraryRoot` | Returns `MediaBrowseTree.rootItem` |
| `onGetChildren(parentId, page, pageSize)` | Paginated children from the tree |
| `onGetItem(mediaId)` | Single-item lookup |
| `onSearch(query)` | Indexes results, notifies template |
| `onGetSearchResult(query, page, pageSize)` | Paginated search results |
| `onCustomCommand` | Handles `CMD_SET_QUEUE`, `CMD_SHUFFLE_PLAY`, `CMD_RESTORE_STATE`, `CMD_TOGGLE_SHUFFLE`, `CMD_TOGGLE_LIKE` |

### MediaMetadata contract

Every song `MediaItem` sets on its `MediaMetadata`:

- `title`, `artist`, `albumTitle`, `artworkUri`, `durationMs`
- Extras bundle with internal re-hydration fields (audio URL, cover URL, IDs,
  popularity, explicit flag) — OEM template does not consume these.

### SessionCommands

| Command | Role in template |
|---|---|
| `CMD_TOGGLE_LIKE` | Powers the heart button on Now Playing |
| `CMD_TOGGLE_SHUFFLE` | Shuffle toggle |
| `CMD_SET_QUEUE` / `CMD_SHUFFLE_PLAY` / `CMD_RESTORE_STATE` | Queue population + cross-device resume |

---

## State Synchronization

```
                  PlaybackService (MediaLibrarySession)
                            |
                +-----------+------------+
                v                        v
         MediaController             OEM Media Template
         (mobile: :app)              (AAOS — first-party OS UI)
                v
      PlayerViewModel (:app)
      - Mini / Expanded mode
      - Like toggling (optimistic + rollback)
      - 250 ms polling
```

The car side now has two controller shapes. The `oem` custom launcher uses
`AutomotivePlayerViewModel` and `AutomotiveContentViewModel` to bridge Compose screens to
repositories and playback. The `playstore` flavor has no launcher; the OEM host is the media
controller and talks to `PlaybackService` directly. `AutomotiveAuthViewModel` remains for
parked sign-in/setup flows.

### BasePlayerStateCollector

Still present in `:core:playback` — used by `PlayerViewModel` on mobile. No subclass in
`:automotive` today. If a later slice introduces a shared collector for custom playback
screens, it should live in `:core:playback` or a car-specific adapter rather than duplicating
mobile polling logic screen by screen.

---

## Variant-specific UI Surfaces (`:automotive`)

### `oem` flavor

- `AutomotiveActivity` is the launcher and hosts the custom 20-screen Compose app.
- It must declare `<meta-data android:name="distractionOptimized" android:value="true" />`
  only after A1's restriction, touch-target and contrast gates hold.
- It must react to `CarUxRestrictions` at runtime: setup, typed search, deep drill-down,
  queue mutation and download deletion are refused while driving; playback controls remain
  available.
- It must not request `RECORD_AUDIO` or draw an in-app microphone recorder. Driving voice
  search is routed through Assistant/system search into `PlaybackService.onSearch` /
  `onGetSearchResult`.

### `playstore` flavor

- No launcher activity is declared.
- Browse, search, queue and playback are host-rendered from `PlaybackService`.
- Any future standalone setup, settings or sign-in activity is parked-only and must not declare
  `distractionOptimized`. Parked-only screens inside `AutomotiveActivity` are enforced by the
  runtime gate instead.

Parked-only settings manifest shape:

```xml
<activity
    android:name=".ui.SettingsActivity"
    android:exported="true"
    android:theme="@style/Theme.NyasaPlayer">
    <intent-filter>
        <action android:name="android.intent.action.APPLICATION_PREFERENCES" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

Do not add a false-valued distraction-optimised activity attribute. Android checks the
activity metadata key only; for parked-only flows, omit the metadata entirely and let the
system block those activities when distraction optimization is required.

### Shared Compose flows

- Auth, PIN opt-in, settings and profile switching are parked-only flows.
- Sign-out confirmation uses two 76dp actions and the gold/dark token pair, not the old
  purple gradient.
- Screen implementation details live in `docs/AAOS_SCREEN_CONTRACT.md`.

### Distraction compliance

| Rule | Our implementation |
|---|---|
| 76 dp min touch target | Enforced via `Modifier.sizeIn(minWidth = 76.dp, minHeight = 76.dp)` on every tappable element |
| Text sizing | Compose text sizes use `sp`, not `dp`, so vehicle font scale is honoured |
| Driving-reachable activity | `AutomotiveActivity` in `src/oem/AndroidManifest.xml` carries `distractionOptimized=true` metadata |
| Parked-only flow | Standalone parked-only activities omit `distractionOptimized`; parked-only screens inside `AutomotiveActivity` are refused by the app gate |
| No typed input while driving | `UX_RESTRICTIONS_NO_KEYBOARD` disables text entry and offers system voice search |
| No setup while driving | `UX_RESTRICTIONS_NO_SETUP` refuses settings, profile switching and PIN setup |
| Back/close affordance | Every overlay and parked flow exposes a 76dp dismiss or back target |

---

## Historical Decision: Option B — Template Path

**Date:** 2026-04-23

**Status:** Superseded for the product decision by `docs/AAOS_PRD.md` §3.3. Retained because
the reasoning still defines the `playstore` variant.

**Context:** Prior to this decision the `:automotive` module contained ~4,500 LOC of
custom Compose (Home, Browse, Library, Full Player, Queue, Mini Player, Artist detail,
error overlays, tab navigation). A review against Google's AAOS media-app rules
concluded that none of these screens could ship on the Play Store AAOS track for a
`<uses name="media" />` app — the OEM template is mandatory for playback and browse.

**Options considered:**

| Option | Play Store AAOS? | Custom UI? | Verdict |
|---|---|---|---|
| A. Drop `<uses name="media" />`, ship custom UI | ❌ | ✅ full | No real distribution path — AAOS head units don't sideload |
| **B. Keep `media`, custom Auth + Settings only** | ✅ | ✅ minimal | Chosen. Production-viable. |
| C. Hybrid — keep custom, bet on per-OEM review | ⚠️ per-OEM | ✅ full | Worst of both; brittle |

**Choice:** B. Distribution via Play Store is the real constraint. The
`:core:playback` layer already does most of the work; the delta is metadata + a Liked
Songs category + a like `SessionCommand`. We lose custom screen personality on the
playback surfaces in exchange for a shippable, OEM-consistent experience.

**Reversibility:** Mostly reversible. The Stitch designs are preserved as reference
(`docs/stitch-screens/`). If AAOS policy ever permits custom media UI (or we pivot to
a non-media automotive category), we can re-introduce custom screens without data
loss.

---

## Firebase Backend Compatibility

Same Firebase project serves `:app`, `oem` AAOS and `playstore` AAOS. The `oem` launcher reads
through the automotive ViewModels and existing repositories; the `playstore` host reads through
MediaBrowser callbacks. No schema changes are required by the AAOS UI upgrade.

### Playback state sync

`PlaybackStatePersistence` (`:core:playback`) periodically saves queue, position, and
repeat mode to `users/{uid}/playbackState/current`. The mobile app writes; the
MediaLibrarySession reads on session creation. Cross-device resume (phone → car) works
automatically because both targets connect to the same session against the same UID.

### Authentication on AAOS

Phone-paired sign-in is preferred (pair once on phone, AAOS inherits) but not yet
implemented. Today AAOS ships Credential Manager Google Sign-In directly
(`CarAuthScreen`); until phone pairing lands, the user signs in once per device.

### Firestore security

Existing rules (catalog read-only for authenticated users; user data scoped to `uid`)
are sufficient. See `firestore.rules`.

---

## Implementation Roadmap

The current AAOS upgrade is governed by:

- `docs/AAOS_PRD.md` — product scope, phases, acceptance criteria.
- `docs/AAOS_SCREEN_CONTRACT.md` — per-screen UI, CTA, state and reuse contract.
- `docs/AAOS_COMPLIANCE.md` — `oem` / `playstore` manifest and runtime compliance gates.
- `docs/superpowers/specs/2026-08-02-aaos-foundation-restrictions-design.md` — A1 spec.
- `docs/superpowers/plans/2026-08-02-aaos-foundation-restrictions.md` — A1 plan.

Near-term sequence:

1. A1 creates tokens, shared primitives, restriction mapping, `CarUiLocation`, `gate()`, and
   `oem` / `playstore` manifest flavors.
2. A2 builds shared chrome and Home against those primitives.
3. A3-A8 implement the remaining 19 screens against `docs/AAOS_SCREEN_CONTRACT.md`.

### Verification

1. `oem` launches `AutomotiveActivity`, declares `distractionOptimized=true`, and enforces
   runtime restrictions while driving.
2. `playstore` declares no launcher activity and remains host-rendered from `PlaybackService`.
3. Both flavors build, test, Detekt and Lint clean.
4. Voice intent (`adb shell am start -a android.intent.action.MEDIA_PLAY_FROM_SEARCH ...`)
   resolves via `PlaybackService.onSearch`.
5. Host-render smoke tests for the `playstore` path run before any Play submission decision.

---

## Key file references

| Component | Path |
|---|---|
| PlaybackService | `core/playback/src/main/java/.../PlaybackService.kt` |
| MediaBrowseTree | `core/playback/src/main/java/.../MediaBrowseTree.kt` |
| SongMediaItemMapper | `core/playback/src/main/java/.../SongMediaItemMapper.kt` |
| PlaybackCommands | `core/playback/src/main/java/.../PlaybackCommands.kt` |
| PlaybackQueueManager | `core/playback/src/main/java/.../PlaybackQueueManager.kt` |
| PlaybackStatePersistence | `core/playback/src/main/java/.../PlaybackStatePersistence.kt` |
| BasePlayerStateCollector | `core/playback/src/main/java/.../BasePlayerStateCollector.kt` |
| CarAuthScreen | `automotive/src/main/java/.../auto/ui/screens/CarAuthScreen.kt` |
| AutomotiveAuthViewModel | `automotive/src/main/java/.../auto/viewmodel/AutomotiveAuthViewModel.kt` |
| AutomotiveActivity | `automotive/src/main/java/.../auto/ui/AutomotiveActivity.kt` |
| AutomotiveApp | `automotive/src/main/java/.../auto/ui/AutomotiveApp.kt` |
| AndroidManifest | `automotive/src/main/AndroidManifest.xml` |
| OEM manifest | `automotive/src/oem/AndroidManifest.xml` |
| Playstore manifest | `automotive/src/playstore/AndroidManifest.xml` |
| automotive_app_desc | `automotive/src/main/res/xml/automotive_app_desc.xml` |
| FirebaseSyncManager | `core/data/src/main/java/.../sync/FirebaseSyncManager.kt` |
| AuthRepository | `core/data/src/main/java/.../api/AuthRepository.kt` |
| UserRepository | `core/data/src/main/java/.../api/UserRepository.kt` |

**Stitch design reference (archived)**: `docs/stitch-screens/` (19 PNGs). See
`docs/stitch-screens/README.md`.
