# NyasaPlayer — Android Automotive OS (AAOS) Architecture

> Reference for the AAOS variant of NyasaPlayer: modules, the shared media session, and how state
> moves between the surfaces. Product scope is `docs/AAOS_PRD.md`; compliance gates are
> `docs/AAOS_COMPLIANCE.md`.

---

## Table of Contents

1. [Overview](#overview)
2. [Module Boundaries](#module-boundaries)
3. [MediaLibrarySession — shared AAOS media surface](#medialibrarysession--shared-aaos-media-surface)
4. [State Synchronization](#state-synchronization)
5. [Variant-specific UI Surfaces (`:automotive`)](#variant-specific-ui-surfaces-automotive)
6. [Historical Decision: Option B — Template Path](#historical-decision-option-b--template-path)
7. [Firebase Backend Compatibility](#firebase-backend-compatibility)
8. [Governing documents](#governing-documents)

---

## Overview

NyasaPlayer AAOS shares the domain, data, and playback layers with the mobile app. The
current product decision is **dual-track by build variant**:

- **`oem`** is the product surface: a custom Compose launcher in `:automotive` (what ships against
  the 20 designed screens is in `AAOS_SHIP_RECORD.md`), distributed through OEM partnership or
  direct install.
- **`playstore`** is the preserved future Play media path: no launcher activity; browse,
  search and playback are host-rendered from `PlaybackService` / `MediaLibraryService`.

`PlaybackService` remains load-bearing in both tracks. It powers the Play media-template
surface, Assistant voice playback/search, media-session controls, queue persistence and
cross-device resume.

**Target display:** landscape head unit; the layout reference is 1440 x 800dp with a 628dp app
window (`aaos-DESIGN.md` §Units).
**Min SDK:** 29.
**`<uses name="media" />`:** required for the preserved Play Store AAOS media variant.

---

## Module Boundaries

```
:core:common  <--  :core:data  <--  :core:playback  -+-- :app (mobile)
                                                     +-- :automotive (AAOS: OEM launcher + Play media variant)
```

Each module's contents are listed in the root `CLAUDE.md` → Module structure. What matters here:
`:automotive` is the AAOS app shell — `oem` adds the custom launcher, `playstore` removes it and
relies on `PlaybackService`, which merges in from `:core:playback`.

### `:automotive` build.gradle (key)

```kotlin
android {
    namespace = "com.example.nyasaplayer.auto"
    defaultConfig { applicationId = "com.example.nyasaplayer"; minSdk = 29 }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:playback"))  // PlaybackService merges from here
    implementation(project(":core:data"))       // AuthRepository, downloads, CrashReporter
    implementation(project(":core:common"))     // Theme + NyasaIcons
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.automotive)
    // android.car.jar is compileOnly — its stubs throw, so restriction logic stays
    // pure functions over primitives (PRD §6.5).
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
 +-- "Liked Songs"        (LIST  — user-specific, from Firestore)
 +-- "Genres"             (GRID  — from Room GenreDao)
 |    +-- "Electronic"
 |    +-- ...
 +-- "Artists"            (GRID  — from Room ArtistDao)
 |    +-- "Jimi Hendrix"
 |    +-- ...
 +-- "All Songs"          (LIST  — from Room SongDao, sorted by popularity)
```

Style hints come from `androidx.media3.session.MediaConstants`
`EXTRAS_KEY_CONTENT_STYLE_{BROWSABLE,PLAYABLE}` with `EXTRAS_VALUE_CONTENT_STYLE_{GRID,LIST}_ITEM`
in the `MediaItem` extras. Depth = Root → Category → Song (3 taps) — within the
AAOS 6-tap limit.

### Callbacks implemented in `PlaybackService`

| Callback | Purpose |
|---|---|
| `onConnect` | Grants the custom `SessionCommand`s below to connecting controllers |
| `onAddMediaItems` | Resolves media-id-only items (the template's `playFromMediaId`, Assistant) to playable ones, downloaded file first |
| `onGetLibraryRoot` | Returns `MediaBrowseTree.rootItem` |
| `onGetChildren(parentId, page, pageSize)` | Paginated children from the tree |
| `onGetItem(mediaId)` | Single-item lookup |
| `onSearch(query)` | Indexes results, notifies template |
| `onGetSearchResult(query, page, pageSize)` | Paginated search results |
| `onCustomCommand` | Handles `CMD_SET_QUEUE`, `CMD_SHUFFLE_PLAY`, `CMD_RESTORE_STATE`, `CMD_TOGGLE_SHUFFLE`, `CMD_TOGGLE_LIKE` |

System media resumption is not supported: media3 routes it through `onPlaybackResumption`, which
`PlaybackService` does not override. The queue's origin (what the queue plays from) reaches
controllers as session extras, set on every timeline change and read by `ControllerConnection`.
Browse items carry the stream URL, not a downloaded file; it is `onAddMediaItems` that resolves
downloads — worth knowing before making browse items directly playable.

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

Both surfaces hold one: `PlayerViewModel` polls position at 250ms, `AutomotivePlayerViewModel` at
500ms. Restore policy (`aaos-DESIGN.md` D61), transport (D62, D63) and reconnection (D65) live in
`:core:playback` behind it, which is why a change there is a change to both surfaces (D61).

---

## Variant-specific UI Surfaces (`:automotive`)

What each flavor must and must not declare, and what the `oem` launcher must refuse while driving,
is `AAOS_COMPLIANCE.md` (`oem` gates OG-1..11, `playstore` gates MG-1..7). In short: `oem`'s
`AutomotiveActivity` is the launcher and declares `distractionOptimized=true`; `playstore` declares
no launcher and is host-rendered from `PlaybackService`; voice search goes through
Assistant/system search into `PlaybackService.onSearch`, and the app never records audio.

A future standalone parked-only activity (settings, say, for MG-4) would take this shape:

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

### Inside the launcher

Settings and profile switching are parked-only sheets, refused under `NO_SETUP` by `gate()`
(`auto/ui/navigation/GateResult.kt`); PIN opt-in is deferred (T18). Per-screen behaviour is
`AAOS_SCREEN_CONTRACT.md`; touch targets, type and contrast are `aaos-DESIGN.md`, each enforced by
a measurement suite (root `CLAUDE.md` → The car surface).

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

**Reversibility:** Mostly reversible, and reversed for `oem` on 2026-08-02 (`docs/AAOS_PRD.md`
§3.3): the custom launcher came back in its own flavor, and `playstore` keeps this decision.

---

## Firebase Backend Compatibility

Same Firebase project serves `:app`, `oem` AAOS and `playstore` AAOS. The `oem` launcher reads
through the automotive ViewModels and existing repositories; the `playstore` host reads through
MediaBrowser callbacks. No schema changes are required by the AAOS UI upgrade.

### Playback state sync

`PlaybackService`, through `PlaybackStatePersistence`, saves the queue, position, repeat mode and
queue origin to `users/{uid}/playbackState/current` on whichever device is playing — every 30s,
on pause, and on destroy. Each surface's player ViewModel restores it with `CMD_RESTORE_STATE`
when its controller connects to an idle player. Phone → car resume works because both read the
same document, not because they share a session.

### Authentication on AAOS

Phone-paired sign-in is preferred (pair once on phone, AAOS inherits) but not implemented.
`CarAuthScreen` signs in with play-services `GoogleSignIn` (the intent flow; mobile uses
Credential Manager), so the user signs in once per device.

### Firestore security

Existing rules (catalog read-only for authenticated users; user data scoped to `uid`)
are sufficient. See `firestore.rules`.

---

## Governing documents

- `docs/AAOS_PRD.md` — product scope, phases, acceptance criteria.
- `docs/AAOS_SCREEN_CONTRACT.md` — per-screen UI, CTA, state and reuse contract.
- `docs/AAOS_COMPLIANCE.md` — `oem` / `playstore` manifest and runtime gates, and how to check them.
- `docs/aaos-DESIGN.md` — visual design and the numbered design decisions.
- `docs/AAOS_SHIP_RECORD.md` — where the app stood against each PRD §12 criterion at ship.

Voice search can be checked with
`adb shell am start -a android.intent.action.MEDIA_PLAY_FROM_SEARCH ...`, which resolves through
`PlaybackService.onSearch`.

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
