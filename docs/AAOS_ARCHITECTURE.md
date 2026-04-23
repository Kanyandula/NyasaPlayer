# NyasaPlayer — Android Automotive OS (AAOS) Architecture

> Reference for the AAOS variant of NyasaPlayer. Updated 2026-04-23 to reflect
> **Option B (Template Path)** — see §6 for the decision and trade-off.

---

## Table of Contents

1. [Overview](#overview)
2. [Module Boundaries](#module-boundaries)
3. [MediaLibrarySession — the AAOS surface](#medialibrarysession--the-aaos-surface)
4. [State Synchronization](#state-synchronization)
5. [Custom Flows (`:automotive`)](#custom-flows-automotive)
6. [Decision: Option B — Template Path](#decision-option-b--template-path)
7. [Firebase Backend Compatibility](#firebase-backend-compatibility)
8. [Implementation Roadmap](#implementation-roadmap)

---

## Overview

NyasaPlayer AAOS shares the domain, data, and playback layers with the mobile app. On
AAOS the **OEM media template** is the UI for Home, Browse, Library, Now Playing, Queue,
and Search — driven directly by our `MediaLibraryService`. The `:automotive` module
ships only the parked-only custom flows Google permits for media apps: Auth, Settings,
and a Sign-Out confirmation dialog.

**Target display:** 1280×720 landscape (16:9), automotive head unit.
**Min SDK:** 28 (AAOS minimum).
**`<uses name="media" />`:** required for Play Store AAOS submission.

---

## Module Boundaries

```
:core:common  <--  :core:data  <--  :core:playback  -+-- :app (mobile)
                                                     +-- :automotive (AAOS: Auth + Settings)
```

| Module | Package | Responsibility |
|---|---|---|
| `:core:common` | `core.common` | Domain models, theme, shared UI primitives, NetworkMonitor |
| `:core:data` | `core.data` | Repositories, Room, Firebase sync, preferences DataStore |
| `:core:playback` | `core.playback` | `PlaybackService` (`MediaLibraryService`), `MediaBrowseTree`, `PlaybackQueueManager`, `PlaybackStatePersistence`, `SongMediaItemMapper`, `PlaybackCommands` |
| `:app` | `com.example.nyasaplayer` | Mobile-only screens, ViewModels, navigation, `MediaController` client |
| `:automotive` | `com.example.nyasaplayer.auto` | AAOS parked-only Activities (Auth, Settings); **no playback/browse UI** |

### `:automotive` build.gradle (key)

```kotlin
android {
    namespace = "com.example.nyasaplayer.auto"
    defaultConfig { applicationId = "com.example.nyasaplayer"; minSdk = 28 }
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

## MediaLibrarySession — the AAOS surface

Under Option B the AAOS-facing API surface is the `MediaLibrarySession.Callback`
installed on `PlaybackService`. The OEM template calls these methods; what we return
is what the user sees.

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

Under Option B the car side does **not** run a `MediaController`-backed ViewModel. The
OEM template is the controller. Our only AAOS ViewModels are `AutomotiveAuthViewModel`
(Sign-In flow) and `CarSettingsViewModel` (Account + Audio Quality prefs).

### BasePlayerStateCollector

Still present in `:core:playback` — used by `PlayerViewModel` on mobile. No subclass in
`:automotive`. If we ever need in-car playback state outside the template (e.g., to
gate something in Settings), we can re-introduce a 500 ms collector at that time.

---

## Custom Flows (`:automotive`)

Three flows ship as Compose. Everything else is template-rendered.

### Auth

- `CarAuthScreen.kt` — Google Sign-In via Credential Manager; CTS-compliant (no keyboard).
- `AutomotiveAuthViewModel.kt` — credential flow + profile creation via `AuthRepository`.
- Entry point: invoked when the MediaLibrarySession returns
  `SessionError.ERROR_PERMISSION_DENIED`, or from Settings → Account → "Sign in again".

### Settings

- `CarSettingsScreen.kt` — vertical list root: **Account**, **Audio Quality**, **About**.
- `CarAccountScreen.kt` — avatar + name + email + Sign Out.
- `CarAudioQualityScreen.kt` — radio list: Low / Normal / High / Very High. Persisted
  via `AudioQualityPreference` DataStore in `:core:data`.
- `CarAboutScreen.kt` — static version / build / Terms / Privacy / Licences.
- `CarSettingsViewModel.kt` — Hilt VM, exposes current audio-quality pref, delegates
  sign-out to `AuthRepository`.

Manifest entry:

```xml
<activity
    android:name=".ui.SettingsActivity"
    android:exported="true"
    android:distractionOptimized="false"
    android:theme="@style/Theme.NyasaPlayer">
    <intent-filter>
        <action android:name="android.intent.action.APPLICATION_PREFERENCES" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

### Sign-Out confirmation

- `SignOutConfirmationDialog.kt` — modal, two 76 dp buttons (Cancel + Sign Out).
- Sign Out button uses the purple **accent gradient** (not red) — sign-out is not
  destructive in the "delete your account" sense.

### Distraction compliance for our custom screens

The OEM template handles distraction rules for all template-rendered surfaces. Our
three custom flows need to meet the bar independently:

| Rule | Our implementation |
|---|---|
| 76 dp min touch target | Enforced via `Modifier.sizeIn(minWidth = 76.dp, minHeight = 76.dp)` on every tappable element |
| 24 sp min body text | `AutomotiveDimens` / typography scale (16/18/20/24/30/36 sp) |
| Parked-only | `android:distractionOptimized="false"` on `SettingsActivity`; the system hides it while driving |
| No keyboard | Auth uses Credential Manager; Settings never prompts for typed input |
| Back affordance | Every Settings sub-screen has a 76 dp circular back button |

---

## Decision: Option B — Template Path

**Date:** 2026-04-23

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

Same Firebase project serves both `:app` and `:automotive`. The AAOS template reads
from our existing repositories via the MediaBrowser callbacks — no schema changes
required for Option B beyond what `:core:data` already provides.

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

The Option-B migration is a single PR on `ek/aaos-ui-redesign` with four phased
commits. Detailed plan at `/Users/admin/.claude/plans/let-s-go-with-b-piped-waffle.md`.

### Commit 1 — Docs & decision record ✅

- Rewrite `docs/AAOS_UI_REDESIGN_PLAN.md`, `docs/AAOS_ARCHITECTURE.md` (this file),
  `CLAUDE.md`, `README.md`, add `docs/stitch-screens/README.md`.

### Commit 2 — `:core:playback` template gap closure

- `MediaBrowseTree`: add `EXTRA_BROWSABLE_STYLE_HINT_*` extras; add Liked Songs root child.
- `SongMediaItemMapper`: set `albumTitle` + `durationMs` on `MediaMetadata` top-level.
- `PlaybackCommands`: add `CMD_TOGGLE_LIKE`.
- `PlaybackService`: register `CMD_TOGGLE_LIKE` + handler; map `PlaybackException` to
  `SessionError.ERROR_{PERMISSION_DENIED, IO, NOT_SUPPORTED}`.
- Extend `MediaBrowseTreeTest`.

### Commit 3 — `:automotive` prune

- Delete 11 screen/component/nav files + 3 ViewModels (AutomotivePlayerViewModel,
  AutomotiveContentViewModel, CarUxRestrictionsHandler).
- Collapse `AutomotiveApp.kt` to Auth + Settings nav host.
- Simplify `AutoAppModule`.
- Clean up `automotive/build.gradle.kts` — drop unused deps after pruning.

### Commit 4 — Manifest + Settings cluster

- Update `AndroidManifest.xml`: remove LAUNCHER from `AutomotiveActivity`, add
  `SettingsActivity` with `APPLICATION_PREFERENCES` intent.
- Build the 4 Settings screens + SignOutConfirmationDialog + CarSettingsViewModel +
  `AudioQualityPreference` DataStore in `:core:data`.
- Wire navigation in `AutomotiveApp.kt`.
- Verify end-to-end on AAOS emulator.

### Verification

Running on Android 13 / API 33 / Automotive-with-Play emulator:

1. Launcher opens the **OEM media template**, not our activity.
2. Browse tree shows grid for Genres/Artists, list for Recently Played / Liked Songs /
   All Songs.
3. Now Playing's Like button persists via `CMD_TOGGLE_LIKE`.
4. Settings gear in the template opens our `SettingsActivity` (parked-only).
5. Settings → Account → Sign Out confirms + returns to Auth.
6. Voice intent (`adb shell am start -a android.intent.action.MEDIA_PLAY_FROM_SEARCH ...`)
   resolves via `onSearch`.

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
| automotive_app_desc | `automotive/src/main/res/xml/automotive_app_desc.xml` |
| FirebaseSyncManager | `core/data/src/main/java/.../sync/FirebaseSyncManager.kt` |
| AuthRepository | `core/data/src/main/java/.../api/AuthRepository.kt` |
| UserRepository | `core/data/src/main/java/.../api/UserRepository.kt` |

**Stitch design reference (archived)**: `docs/stitch-screens/` (19 PNGs). See
`docs/stitch-screens/README.md`.
